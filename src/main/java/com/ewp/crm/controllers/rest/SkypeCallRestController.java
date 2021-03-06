package com.ewp.crm.controllers.rest;

import com.ewp.crm.models.AssignSkypeCall;
import com.ewp.crm.models.Client;
import com.ewp.crm.models.ClientHistory;
import com.ewp.crm.models.User;
import com.ewp.crm.service.interfaces.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RestController
public class SkypeCallRestController {
	private static Logger logger = LoggerFactory.getLogger(SkypeCallRestController.class);

	private final AssignSkypeCallService assignSkypeCallService;
	private final ClientHistoryService clientHistoryService;
	private final ClientService clientService;
	private final UserService userService;
	private final RoleService roleService;

	@Autowired
	public SkypeCallRestController(AssignSkypeCallService assignSkypeCallService,
								   ClientHistoryService clientHistoryService,
								   ClientService clientService,
								   UserService userService,
								   RoleService roleService) {
		this.assignSkypeCallService = assignSkypeCallService;
		this.clientHistoryService = clientHistoryService;
		this.clientService = clientService;
		this.userService = userService;
		this.roleService = roleService;
	}

	@GetMapping(value = "rest/skype/{clientId}", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN', 'USER')")
	public ResponseEntity<AssignSkypeCall> getAssignSkypeCallByClientId(@PathVariable Long clientId) {
		Optional<AssignSkypeCall> assignSkypeCall = assignSkypeCallService.getAssignSkypeCallByClientId(clientId);
		return assignSkypeCall.map(ResponseEntity::ok).orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
	}

	@GetMapping(value = "rest/skype/allMentors", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN', 'USER')")
	public ResponseEntity<List<User>> getAllMentors() {
		List<User> users = userService.getByRole(roleService.getRoleByName("MENTOR"));
		return ResponseEntity.ok(users);
	}

	@PostMapping(value = "rest/skype/addSkypeCallAndNotification")
	@PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN', 'USER')")
	public ResponseEntity addSkypeCallAndNotification(@AuthenticationPrincipal User userFromSession,
													  @RequestParam Long startDate,
													  @RequestParam Long clientId) {
		Optional<Client> client = clientService.getClientByID(clientId);
		if (client.isPresent()) {
			ZonedDateTime dateSkypeCall = Instant.ofEpochMilli(startDate).atZone(ZoneId.of("+00:00")).withZoneSameLocal(ZoneId.of("Europe/Moscow"));
			ZonedDateTime notificationBeforeOfSkypeCall = Instant.ofEpochMilli(startDate).atZone(ZoneId.of("+00:00")).withZoneSameLocal(ZoneId.of("Europe/Moscow")).minusHours(1);
			try {
				AssignSkypeCall clientAssignSkypeCall = new AssignSkypeCall(userFromSession, client.get(), ZonedDateTime.now(), dateSkypeCall, notificationBeforeOfSkypeCall);
				assignSkypeCallService.addSkypeCall(clientAssignSkypeCall);
				client.get().setLiveSkypeCall(true);
				clientHistoryService.createHistory(userFromSession, client.get(), ClientHistory.Type.SKYPE).ifPresent(client.get()::addHistory);
				clientService.update(client.get());
				logger.info("{} добавил клиенту id:{} звонок по скайпу на {}", userFromSession.getFullName(), client.get().getId(), dateSkypeCall);

				return ResponseEntity.ok(HttpStatus.OK);
			} catch (Exception e) {
				logger.error("{} не смог добавить клиенту id:{} звокон по скайпу на {}", userFromSession.getFullName(), client.get().getId(), startDate, e);
				return ResponseEntity.badRequest().body("Произошла ошибка.");
			}
		}
		logger.info("{} не смог добавить клиенту id:{} звокон по скайпу на {} (Клиент не найден)", userFromSession.getFullName(), clientId, startDate);
		return ResponseEntity.badRequest().body("Произошла ошибка.");
	}

	@PostMapping(value = "rest/mentor/updateEvent")
	@PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN', 'USER')")
	public ResponseEntity updateEvent(@AuthenticationPrincipal User userFromSession,
									  @RequestParam(name = "clientId") Long clientId,
									  @RequestParam Long skypeCallDateNew,
									  @RequestParam Long skypeCallDateOld) {
		Client client = clientService.get(clientId);
		try {
			Optional<AssignSkypeCall> assignSkypeCall = assignSkypeCallService.getAssignSkypeCallByClientId(client.getId());
			if (assignSkypeCall.isPresent()) {
				assignSkypeCall.get().setCreatedTime(ZonedDateTime.now());
				ZonedDateTime dateSkypeCall = Instant.ofEpochMilli(skypeCallDateNew).atZone(ZoneId.of("+00:00")).withZoneSameLocal(ZoneId.of("Europe/Moscow"));
				assignSkypeCall.get().setWhoCreatedTheSkypeCall(userFromSession);
				assignSkypeCall.get().setTheNotificationWasIsSent(false);
				if (!Objects.equals(skypeCallDateNew, skypeCallDateOld)) {
					assignSkypeCall.get().setSkypeCallDate(dateSkypeCall);
					assignSkypeCall.get().setNotificationBeforeOfSkypeCall(Instant.ofEpochMilli(skypeCallDateNew).atZone(ZoneId.of("+00:00")).withZoneSameLocal(ZoneId.of("Europe/Moscow")).minusHours(1));
					clientHistoryService.createHistory(userFromSession, client, ClientHistory.Type.SKYPE_UPDATE).ifPresent(client::addHistory);
				}
				assignSkypeCallService.update(assignSkypeCall.get());
				clientService.updateClient(client);
				logger.info("{} изменил клиенту id:{} звонок по скайпу на {}", userFromSession.getFullName(), client.getId(), dateSkypeCall);
				return ResponseEntity.ok(HttpStatus.OK);
			} else {
				logger.info("{} не смог изменить клиенту id:{} звокон по скайпу на {} (assignSkypeCall is null)", userFromSession.getFullName(), client.getId(), skypeCallDateNew);
				return ResponseEntity.badRequest().body("Произошла ошибка.");
			}
		} catch (Exception e) {
			logger.info("{} не смог изменить клиенту id:{} звокон по скайпу на {}", userFromSession.getFullName(), client.getId(), skypeCallDateNew, e);
			return ResponseEntity.badRequest().body("Произошла ошибка.");
		}
	}

	@PostMapping(value = "rest/mentor/deleteEvent")
	@PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN', 'USER')")
	public ResponseEntity deleteEvent(@AuthenticationPrincipal User principal,
									  @RequestParam(name = "clientId") Long clientId,
									  @RequestParam Long skypeCallDateOld) {
	    Client client = clientService.get(clientId);
	    client.setLiveSkypeCall(false);
	    clientHistoryService.createHistory(principal, client, ClientHistory.Type.SKYPE_DELETE).ifPresent(client::addHistory);
	    clientService.updateClient(client);
	    Optional<AssignSkypeCall> assignSkypeCall = assignSkypeCallService.getAssignSkypeCallByClientId(client.getId());
	    if (assignSkypeCall.isPresent()) {
	        assignSkypeCallService.deleteByIdSkypeCall(assignSkypeCall.get().getId());
	        logger.info("{} удалил клиенту id:{} звонок по скайпу на {}", principal.getFullName(), client.getId(), skypeCallDateOld);
	        return ResponseEntity.ok(HttpStatus.OK);
	    }
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
	}
}