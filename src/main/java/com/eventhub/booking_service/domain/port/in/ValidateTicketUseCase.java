package com.eventhub.booking_service.domain.port.in;

import com.eventhub.booking_service.domain.model.Ticket;

public interface ValidateTicketUseCase {
    Ticket validateAndUseTicket(String ticketId, String qrCodeHash);
}