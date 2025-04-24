package de.ait.restaurantapp.services;

import de.ait.restaurantapp.dto.EmailDto;
import de.ait.restaurantapp.dto.ReservationFormDto;
import de.ait.restaurantapp.enums.ReservationStatus;
import de.ait.restaurantapp.exception.NoAvailableTableException;
import de.ait.restaurantapp.model.Reservation;
import de.ait.restaurantapp.model.RestaurantTable;
import de.ait.restaurantapp.repositories.ReservationRepo;
import de.ait.restaurantapp.repositories.RestaurantTableRepo;
import de.ait.restaurantapp.utils.ReservationIDGenerator;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.*;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Сервис для управления резервированием столиков в ресторане.
 * Основные функции:
 * - создание нового резервирования
 * - получение списка всех резервирований
 * - отмена существующего резервирования
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepo reservationRepo;
    private final RestaurantTableRepo tableRepository;
    private final EmailService emailService;

    @Value("${restaurant.opening-time}")
    private String openingTimeString;

    @Value("${restaurant.closing-time}")
    private String closingTimeString;

    /**
     * Создает новое резервирование на основе данных из формы.
     * Добавлена валидация входных данных, установка флага isAdmin,
     * проверка «не более одной брони в день на один email» (исправлено)
     * и более точные исключения.
     */
    @Override
    @Transactional
    public Reservation createReservation(ReservationFormDto form) throws MessagingException {
        // --- Валидация входных параметров ---
        log.debug("Creating reservation - start | form: guestCount={}, timeRange={} to {}", form.getGuestNumber(), form.getStartDateTime(), form.getEndTime());
        Objects.requireNonNull(form, "ReservationFormDto must not be null");
        LocalDateTime startDateTime = Objects.requireNonNull(form.getStartDateTime(), "startDateTime must not be null");
        LocalTime endTime = Objects.requireNonNull(form.getEndTime(), "endTime must not be null");
        LocalDate endDate = startDateTime.toLocalDate();
        LocalDateTime endDateTime = LocalDateTime.of(endDate, endTime);

        if (!startDateTime.isBefore(endDateTime)) {
            log.warn("Start date/time must be before end time | SDT: {}, ET: {}", startDateTime, endDateTime);
            throw new IllegalArgumentException("Start date/time must be before end time");
        }
        if (startDateTime.isBefore(LocalDateTime.now())) {
            log.warn("Start date/time must be in the future | SDT: {}, EDT: {}", startDateTime, endDateTime);
            throw new IllegalArgumentException("Start date/time must be in the future");
        }

        // --- Ограничение: не более одной резервации в один день на один email ---
        LocalDate reservationDate = startDateTime.toLocalDate();
        LocalDateTime startOfDay = reservationDate.atStartOfDay();
        LocalDateTime endOfDay = reservationDate.plusDays(1).atStartOfDay();

        boolean alreadyHasBooking = !reservationRepo
                .findByCustomerEmailIgnoreCaseAndReservationStatusAndStartDateTimeBetween(
                        form.getCustomerEmail(),
                        ReservationStatus.CONFIRMED,
                        startOfDay,
                        endOfDay
                ).isEmpty();

        if (alreadyHasBooking) {
            throw new IllegalArgumentException(
                    "Email " + form.getCustomerEmail() +
                            " already has a reservation on " + reservationDate
            );
        }
        // ---------------------------------------------------------------

        // --- Ограничение по времени работы ресторана---
        LocalTime openingTime = LocalTime.parse(openingTimeString);
        LocalTime closingTime = LocalTime.parse(closingTimeString);
        LocalTime startTime = startDateTime.toLocalTime();
        //LocalTime endTime     = endDateTime.toLocalTime();
        if (startTime.isBefore(openingTime) || endTime.isAfter(closingTime)) {
            throw new IllegalArgumentException(
                    "Reservations are only allowed between " +
                            openingTime + " and " + closingTime
            );
        }

        // --- Определяем, создал ли текущий пользователь как админ ---
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        log.debug("Is user admin: {}", isAdmin);

        // --- Поиск подходящих столиков по вместимости ---
        List<RestaurantTable> availableTables = tableRepository.findAll().stream()
                .filter(t -> t.getCapacity() >= form.getGuestNumber())
                .sorted(Comparator.comparingInt(RestaurantTable::getCapacity))
                .toList();

        if (availableTables.isEmpty()) {
            log.warn("No tables available | guests: {}, time: {}", form.getGuestNumber(), form.getStartDateTime());
            throw new NoAvailableTableException();
        }

        // --- Проверка наличия свободного столика по времени ---
        for (RestaurantTable table : availableTables) {
            boolean hasConflict = reservationRepo
                    .findByRestaurantTable_IdAndReservationStatusAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                            table.getId(),
                            ReservationStatus.CONFIRMED,
                            endDateTime,
                            startDateTime
                    )
                    .stream()
                    .findAny()
                    .isPresent();

            if (!hasConflict) {
                // Генерация кода и создание сущности с указанием isAdmin
                String reservationCode = ReservationIDGenerator.generateReservationId();
                Reservation reservation = Reservation.builder()
                        .reservationCode(reservationCode)
                        .customerName(form.getCustomerName())
                        .customerEmail(form.getCustomerEmail())
                        .guestCount(form.getGuestNumber())
                        .startDateTime(startDateTime)
                        .endDateTime(endDateTime)
                        .restaurantTable(table)
                        .reservationStatus(ReservationStatus.CONFIRMED)
                        .isAdmin(isAdmin)
                        .build();
                log.debug("Saving reservation: {}", reservation);

                // Сохраняем в БД до отправки письма
                Reservation saved = reservationRepo.save(reservation);
                log.info("Reservation created | code: {}, table: {}, customer: {}", reservationCode, table.getId(), form.getCustomerEmail());

                // Отправка email-подтверждения
                EmailDto emailClientDto = new EmailDto();
                emailClientDto.setTo(saved.getCustomerEmail());
                emailClientDto.setName(saved.getCustomerName());
                emailClientDto.setReservationCode(reservationCode);
                emailClientDto.setStartTime(saved.getStartDateTime());
                emailClientDto.setEndTime(saved.getEndDateTime());
                emailClientDto.setGuestCount(saved.getGuestCount());
                emailClientDto.setSubject("Подтверждение бронирования столика");
                emailService.sendHTMLEmail(emailClientDto);

                return saved;
            }
        }

        // Бросаем специальное исключение при отсутствии свободных столиков
        log.warn("No available tables for the specified time and guest count");
        throw new NoAvailableTableException(
                "No available tables for the specified time and guest count"
        );
    }

    @Override
    public List<Reservation> getAllReservations() {
        log.debug("Fetching all reservations");
        List<Reservation> reservations = reservationRepo.findAll();
        log.debug("Found {} reservations.", reservations.size());
        return reservations;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> getReservationsForTableToday(Integer tableId) {
        log.debug("Fetching today's reservations for table {}", tableId);
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime startOfNextDay = today.plusDays(1).atStartOfDay();

        List<Reservation> reservations = reservationRepo
                .findByRestaurantTable_IdAndStartDateTimeGreaterThanEqualAndStartDateTimeLessThanAndReservationStatus(
                        tableId,
                        startOfDay,
                        startOfNextDay,
                        ReservationStatus.CONFIRMED
                );
        if (reservations.isEmpty()) {
            log.info("No reservations found for table {} today.", tableId);
        } else {
            log.debug("Found {} reservations for table {} today", reservations.size(), tableId);
        }
        return reservations;
    }

    @Transactional
    public List<Reservation> getAllReservationByDay(LocalDate day) {
        log.debug("Fetching all reservations by day: {}", day);
        List<Reservation> reservations = reservationRepo.findAll().stream()
                .filter(r -> r.getStartDateTime().toLocalDate().equals(day)).collect(Collectors.toList());
        log.debug("Found {} reservations by day: {}", reservations.size(), day);

        return reservations;
    }

    @Override
    public boolean cancelReservation(String reservationCode) {
        log.debug("Attempting to cancel a reservation by code: {}", reservationCode);
        return reservationRepo.findByReservationCode(reservationCode)
                .map(reservation -> {
                    if (reservation.getReservationStatus() == ReservationStatus.CANCELED) {
                        log.warn("Cancel failed - reservation {} is already canceled", reservationCode);
                        return false;
                    }

                    if (reservation.getReservationCode().equals(reservationCode)) {
                        EmailDto emailClientDto = new EmailDto();
                        emailClientDto.setTo(reservation.getCustomerEmail());
                        emailClientDto.setName(reservation.getCustomerName());
                        emailClientDto.setStartTime(reservation.getStartDateTime());
                        emailClientDto.setEndTime(reservation.getEndDateTime());
                        emailClientDto.setGuestCount(reservation.getGuestCount());
                        emailClientDto.setSubject("Подтверждение отмены бронирования столика");
                        emailClientDto.setCancel(true);

                        try {
                            emailService.sendHTMLEmail(emailClientDto);
                        } catch (MessagingException e) {
                            log.error("Failed to send HTMLEmail {}", e);
                            throw new RuntimeException(e);
                        }
                    }

                    reservation.setReservationStatus(ReservationStatus.CANCELED);
                    reservationRepo.save(reservation);
                    log.info("Reservation {} canceled successfully", reservationCode);
                    return true;
                })
                .orElseGet(() -> {
                    log.warn("Cancel failed - reservation {} not found", reservationCode);
                    return false;
                });
    }
}