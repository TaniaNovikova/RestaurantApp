package de.ait.restaurantapp.controller;

import de.ait.restaurantapp.dto.ReservationFormDto;
import de.ait.restaurantapp.exception.NoAvailableTableException;
import de.ait.restaurantapp.model.Reservation;
import de.ait.restaurantapp.services.FileService;
import de.ait.restaurantapp.services.ReservationService;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/restaurant/admin")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminPageController {

    private final ReservationService reservationService;
    private final FileService fileService;


    public AdminPageController(ReservationService reservationService, FileService fileService) {
        this.reservationService = reservationService;
        this.fileService = fileService;
    }

    @ModelAttribute("reservationForm")
    public ReservationFormDto getReservationFormDto() {
        return new ReservationFormDto();
    }

    @GetMapping()
    public String showAdminPanel() {
        return "admin-panel";
    }

    @PostMapping("/reserve")
    public String createdReservationFromAdmin(@ModelAttribute ReservationFormDto form, Model model) {
        try {
            Reservation reservation = reservationService.createReservation(form);
            model.addAttribute("message", "Reservation created successfully!" +
                    "Reservation code is: " + reservation.getReservationCode());
        } catch (MessagingException | IllegalArgumentException e) {
            model.addAttribute("message", "Error: " + e.getMessage());
            model.addAttribute("reservationForm", form);
        } catch (NoAvailableTableException exception) {
            model.addAttribute("message", exception.getMessage());
            model.addAttribute("reservationForm", form);
        }
        return "admin-panel";
    }

    @PostMapping("/cancel")
    public String cancelReservationFromAdmin(@RequestParam String reservationCode, Model model) {
        boolean success = reservationService.cancelReservation(reservationCode);

        if (success) {
            model.addAttribute("message", "Reservation cancelled successfully!");
        } else {
            model.addAttribute("message", "Reservation Code is not found");
        }
        model.addAttribute("reservationCode", "");
        return "admin-panel";
    }

    @GetMapping("/reservations/today")
    public String getReservationsToday(@RequestParam Integer tableNumber, Model model) {
        List<Reservation> tableReservations = reservationService.getReservationsForTableToday(tableNumber);
        if (tableReservations.isEmpty()) {
            model.addAttribute("message", "No reservations found for table " + tableNumber);
        }
        model.addAttribute("tableReservations", tableReservations);
        return "admin-panel";
    }

    @GetMapping("/reservations/confirmed/by-date")
    public String getReservationsConfirmedByDate(@RequestParam LocalDate date, Model model) {
        List<Reservation> reservationsByDate = reservationService.getAllReservationByDay(date);
        if (reservationsByDate.isEmpty()) {
            model.addAttribute("message", "No reservations found for date: " + date);
        }
        model.addAttribute("allReservations", reservationsByDate);
        return "admin-panel";
    }

    @PostMapping("/upload-menu")
    public String uploadMenu(@RequestParam("file") MultipartFile file, Model model) {
        if (file.isEmpty() || !file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            model.addAttribute("message", "Invalid file");
            return "admin-panel";
        }
        try {
            fileService.saveMenuInProjectDir(file);
            model.addAttribute("message", "Menu uploaded successfully!");
        } catch (IllegalArgumentException | IOException e) {
            log.error("File upload failed", e);
            model.addAttribute("message", "Error: " + e.getMessage());
        }
        return "admin-panel";
    }
}