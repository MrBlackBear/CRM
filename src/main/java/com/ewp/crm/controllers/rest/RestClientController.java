package com.ewp.crm.controllers.rest;

import com.ewp.crm.models.Client;
import com.ewp.crm.models.ClientHistory;
import com.ewp.crm.models.FilteringCondition;
import com.ewp.crm.models.User;
import com.ewp.crm.service.interfaces.ClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.util.List;

@RestController
@RequestMapping("admin/rest/client")
public class RestClientController {

    private static Logger logger = LoggerFactory.getLogger(RestClientController.class);

    private final ClientService clientService;

    @Autowired
    public RestClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Client>> getAll() {
        return ResponseEntity.ok(clientService.getAllClients());
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Client> getClientByID(@PathVariable Long id) {
        return ResponseEntity.ok(clientService.getClientByID(id));
    }

    @RequestMapping(value = "/assign", method = RequestMethod.POST)
    public ResponseEntity<User> assign(@RequestParam(name = "clientId") Long clientId) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Client client = clientService.getClientByID(clientId);
        if (client.getOwnerUser() != null) {
            logger.info("User {} tried to assign a client with id {}, but client have owner", user.getEmail(), clientId);
            return ResponseEntity.badRequest().body(null);
        }
        client.setOwnerUser(user);
        clientService.updateClient(client);
        logger.info("User {} has assigned client with id {}", user.getEmail(), clientId);
        return ResponseEntity.ok(client.getOwnerUser());
    }

    @RequestMapping(value = "/unassign", method = RequestMethod.POST)
    public ResponseEntity unassign(@RequestParam(name = "clientId") Long clientId) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Client client = clientService.getClientByID(clientId);
        if (client.getOwnerUser() == null) {
            logger.info("User {} tried to unassign a client with id {}, but client already doesn't have owner", user.getEmail(), clientId);
            return ResponseEntity.badRequest().build();
        }
        client.setOwnerUser(null);
        clientService.updateClient(client);
        logger.info("User {} has unassigned client with id {}", user.getEmail(), clientId);
        return ResponseEntity.ok(client.getOwnerUser());
    }

    @RequestMapping(value = "/update", method = RequestMethod.POST)
    public ResponseEntity updateClient(@RequestBody Client client) {
        User currentAdmin = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        client.setHistory(clientService.getClientByID(client.getId()).getHistory());
        client.addHistory(new ClientHistory(currentAdmin.getFullName() + " изменил профиль клиента"));
        clientService.updateClient(client);
        logger.info("{} has updated client: id {}, email {}", currentAdmin.getFullName(), client.getId(), client.getEmail());
        return ResponseEntity.ok(HttpStatus.OK);
    }

    @RequestMapping(value = "/delete/{id}", method = RequestMethod.POST)
    public ResponseEntity deleteClient(@PathVariable Long id) {
        clientService.deleteClient(id);
        User currentAdmin = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        logger.info("{} has deleted client with id {}", currentAdmin.getFullName(), id);
        return ResponseEntity.ok(HttpStatus.OK);
    }

    @RequestMapping(value = "/addClient", method = RequestMethod.POST)
    public ResponseEntity addClient(@RequestBody Client client) {
        User currentAdmin = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        client.addHistory(new ClientHistory(currentAdmin.getFullName() + " добавил клиента"));
        clientService.addClient(client);
        logger.info("{} has added client: id {}, email {}", currentAdmin.getFullName(), client.getId(), client.getEmail());
        return ResponseEntity.ok(HttpStatus.OK);
    }

    @RequestMapping(value = "/filtration", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Client>> getAllWithConditions(@RequestBody FilteringCondition filteringCondition) {
        return ResponseEntity.ok(clientService.filteringClient(filteringCondition));
    }

}