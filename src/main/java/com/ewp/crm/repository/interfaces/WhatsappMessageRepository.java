package com.ewp.crm.repository.interfaces;

import com.ewp.crm.models.whatsapp.WhatsappMessage;

import java.util.List;

public interface WhatsappMessageRepository extends CommonGenericRepository<WhatsappMessage> {

    WhatsappMessage findByMessageNumber(long messageNumber);

    List<WhatsappMessage> findTop40BySeenFalseAndClient_IdOrderByTimeDesc(long clientId);

    long countAllBySeenFalseAndClient_Id(long clientId);

    List<WhatsappMessage> findAllByClient_Id(long clientId);
    WhatsappMessage findTopByClient_IdOrderByTimeDesc(long clientId);
}