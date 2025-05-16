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

/**
 * Controller for administrative actions in the restaurant application.
 */
@Controller // Marks this class as a Spring MVC controller
@RequestMapping("/restaurant/admin") // Base URL mapping for all handler methods
@PreAuthorize("hasRole('ADMIN')") // Ensures only users with ADMIN role can access these endpoints
@Slf4j // Lombok annotation to generate a logger (log) for the class
public class AdminPageController {

    private final ReservationService reservationService;
    private final FileService fileService;

    /**
     * Constructor injection for required services.
     *
     * @param reservationService service to manage reservations
     * @param fileService        service to handle file operations
     */
    public AdminPageController(ReservationService reservationService, FileService fileService) {
        this.reservationService = reservationService;
        this.fileService = fileService;
    }

    /**
     * Adds an empty ReservationFormDto to the model before any handler method.
     * Used to bind form data for reservation creation.
     *
     * @return a new instance of ReservationFormDto
     */
    @ModelAttribute("reservationForm") // Binds the return value to the model attribute named 'reservationForm'
    public ReservationFormDto getReservationFormDto() {
        return new ReservationFormDto();
    }

    /**
     * Displays the main admin panel view.
     *
     * @return name of the Thymeleaf template for the admin panel
     */
    @GetMapping // Handles HTTP GET requests to '/restaurant/admin'
    public String showAdminPanel() {
        return "admin-panel";
    }

    /**
     * Processes reservation creation requests from the admin panel.
     * Catches various exceptions to provide feedback in the UI.
     *
     * @param form  form data for the new reservation
     * @param model Spring Model to pass feedback messages and data to the view
     * @return the admin panel view name
     */
    @PostMapping("/reserve") // Handles HTTP POST requests to '/restaurant/admin/reserve'
    public String createdReservationFromAdmin(@ModelAttribute ReservationFormDto form, Model model) {
        try {
            Reservation reservation = reservationService.createReservation(form);
            model.addAttribute("message", "Reservation created successfully! " +
                    "Reservation code is: " + reservation.getReservationCode());
        } catch (MessagingException | IllegalArgumentException e) {
            // Handles email errors or invalid arguments
            model.addAttribute("message", "Error: " + e.getMessage());
            model.addAttribute("reservationForm", form);
        } catch (NoAvailableTableException exception) {
            // Handles case when no tables are available
            model.addAttribute("message", exception.getMessage());
            model.addAttribute("reservationForm", form);
        }
        return "admin-panel";
    }

    /**
     * Cancels an existing reservation based on its code.
     *
     * @param reservationCode unique code identifying the reservation
     * @param model           Spring Model to pass feedback to the view
     * @return the admin panel view name
     */
    @PostMapping("/cancel") // Handles HTTP POST requests to '/restaurant/admin/cancel'
    public String cancelReservationFromAdmin(@RequestParam String reservationCode, Model model) {
        boolean success = reservationService.cancelReservation(reservationCode);

        if (success) {
            model.addAttribute("message", "Reservation cancelled successfully!");
        } else {
            model.addAttribute("message", "Reservation code not found");
        }
        model.addAttribute("reservationCode", "");
        return "admin-panel";
    }

    /**
     * Retrieves all reservations for a specific table for today.
     *
     * @param tableNumber the number of the table to query
     * @param model       Spring Model to pass reservation list or messages to the view
     * @return the admin panel view name
     */
    @GetMapping("/reservations/today") // Handles HTTP GET requests to '/restaurant/admin/reservations/today'
    public String getReservationsToday(@RequestParam Integer tableNumber, Model model) {
        List<Reservation> tableReservations = reservationService.getReservationsForTableToday(tableNumber);
        if (tableReservations.isEmpty()) {
            model.addAttribute("message", "No reservations found for table " + tableNumber);
        }
        model.addAttribute("tableReservations", tableReservations);
        return "admin-panel";
    }

    /**
     * Retrieves all confirmed reservations for a given date.
     *
     * @param date  the date to query (format: yyyy-MM-dd)
     * @param model Spring Model to pass reservation list or messages to the view
     * @return the admin panel view name
     */
    @GetMapping("/reservations/confirmed/by-date") // Handles HTTP GET requests to '/restaurant/admin/reservations/confirmed/by-date'
    public String getReservationsConfirmedByDate(@RequestParam LocalDate date, Model model) {
        List<Reservation> reservationsByDate = reservationService.getAllReservationByDay(date);
        if (reservationsByDate.isEmpty()) {
            model.addAttribute("message", "No reservations found for date: " + date);
        }
        model.addAttribute("allReservations", reservationsByDate);
        return "admin-panel";
    }

    /**
     * Handles uploading of the menu PDF by the admin.
     * Validates file type and delegates save operation to FileService.
     *
     * @param file  uploaded multipart file (must be a PDF)
     * @param model Spring Model to pass feedback to the view
     * @return the admin panel view name
     */
    @PostMapping("/upload-menu") // Handles HTTP POST requests to '/restaurant/admin/upload-menu'
    public String uploadMenu(@RequestParam("file") MultipartFile file, Model model) {
        // Validate file presence and extension
        if (file.isEmpty() || !file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            model.addAttribute("message", "Invalid file");
            return "admin-panel";
        }
        try {
            fileService.saveMenuInProjectDir(file); // Save the PDF to the project directory
            model.addAttribute("message", "Menu uploaded successfully!");
        } catch (IllegalArgumentException | IOException e) {
            // Log error and inform admin of failure
            log.error("File upload failed", e);
            model.addAttribute("message", "Error: " + e.getMessage());
        }
        return "admin-panel";
    }
}
