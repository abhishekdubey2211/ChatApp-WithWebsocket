package com.employee.service;

import com.employee.model.Message;
import com.employee.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketHandlerTest extends TextWebSocketHandler {

    private static final Map<String, UserDetails> users = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> groupMembers = new ConcurrentHashMap<>();
    private static final Map<String, String> groupNames = new ConcurrentHashMap<>();
    private static final Map<String, String> contactUserMap = new ConcurrentHashMap<>();

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebSocketHandlerTest(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = session.getUri().getQuery().replace("token=", "");
        String username = jwtUtil.isTokenValid(token);
        if (username != null) {
            UserDetails userDetails = new UserDetails();
            userDetails.setUseruniqueId (String.valueOf(jwtUtil.getClaims(token, "useruniqueid")));
            userDetails.setUsercontact(String.valueOf(jwtUtil.getClaims(token, "contact")));
            userDetails.setSession(session);
            userDetails.setUsername(username);
            users.put(username, userDetails);
            sendJsonMessage(session, "system", username + " Connected Successfully.", username);
            sendBroadcastMessage("System", username + " has joined the chat!");
        } else {
            session.close(CloseStatus.NOT_ACCEPTABLE);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            Message chatMessage = objectMapper.readValue(message.getPayload(), Message.class);
            String sender = chatMessage.getSender();

            switch (chatMessage.getType().toLowerCase()) {
                case "private":
                    sendPrivateMessage(sender, chatMessage.getReceiver(), chatMessage.getContent());
                    break;
                case "create_group":
                    createGroup(chatMessage.getGroup(), sender, session);
                    break;
                case "join_group":
                    joinGroup(chatMessage.getGroup(), sender, session);
                    break;
                case "leave_group":
                    leaveGroup(chatMessage.getGroup(), sender, session);
                    break;
                case "group":
                    sendGroupMessage(sender, chatMessage.getGroup(), chatMessage.getContent());
                    break;
                case "broadcast":
                    sendBroadcastMessage(sender, chatMessage.getContent());
                    break;
                case "users":
                    sendActiveUsers(session);
                    break;
                default:
                    sendJsonMessage(session, "error", "Invalid message type!", sender);
            }
        } catch (IOException e) {
            sendJsonMessage(session, "error", "Error processing message: " + e.getMessage(), "system");
        }
    }

    private void sendActiveUsers(WebSocketSession session) throws IOException {
        List<String> activeUsers = new ArrayList<>(users.keySet());
        sendJsonMessage(session, "active_users", objectMapper.writeValueAsString(activeUsers), "system");
    }
    private void sendBroadcastMessage(String sender, String message) throws IOException {
        for (UserDetails userDetails : users.values()) {  // Iterate over UserDetails objects
            WebSocketSession session = userDetails.getSession();  // Get the WebSocketSession
            if (session != null && session.isOpen()) {  // Ensure session is not null and open
                sendJsonMessage(session, "broadcast", message, sender);
            }
        }
    }

    private void createGroup(String groupName, String creator, WebSocketSession session) throws IOException {
        String groupId = UUID.randomUUID().toString();
        groupMembers.putIfAbsent(groupId, new HashSet<>());
        groupMembers.get(groupId).add(users.get(creator).useruniqueId);
        groupNames.put(groupId, groupName);
        sendJsonMessage(session, "system", "Group '" + groupName + "' created successfully. -GroupId : " + groupId, creator);
        sendGroupMessage(creator,groupId,"Group '" + groupName + "' created and you have been invited to join it.");
    }


    private void joinGroup(String groupId, String username, WebSocketSession session) throws IOException {
        if (!groupMembers.containsKey(groupId)) {
            sendJsonMessage(session, "error", "Group does not exist!", "system");
            return;
        }
        groupMembers.get(groupId).add(users.get(username).useruniqueId);
        sendJsonMessage(session, "system", "You have joined the group!", username);
        sendGroupMessage("System",groupId,username+" has joined the group");
    }

    private void leaveGroup(String groupId, String username, WebSocketSession session) throws IOException {
        if (!groupMembers.containsKey(groupId) || !groupMembers.get(groupId).contains(users.get(username).getUseruniqueId())) {
            sendJsonMessage(session, "error", "You are not in this group!", "system");
            return;
        }
        // Remove the user from the group
        groupMembers.get(groupId).remove(users.get(username).getUseruniqueId());
        // If the group is now empty, remove it
        if (groupMembers.get(groupId).isEmpty()) {
            groupMembers.remove(groupId);
        }
        // Notify the user that they have left
        sendJsonMessage(session, "system", "You have left the group!", username);
        // Notify remaining group members that the user has left
        sendGroupMessage("system", groupId, username + " has left the group.");
    }


    private void sendGroupMessage(String sender, String groupId, String message) throws IOException {
        if (groupMembers.containsKey(groupId)) {  // Check if the group exists
            for (String userUniqueId : groupMembers.get(groupId)) {  // Iterate over group members
                System.out.println("Group Members for " + groupId + ": " + groupMembers.get(groupId)+
                        "useruniqueid: "+userUniqueId
                        );

                Optional<UserDetails> userDetails = users.values().stream()
                        .filter(u -> u.getUseruniqueId().equals(userUniqueId))
                        .findFirst();

                if (userDetails.isPresent()) {  // Ensure user exists
                    WebSocketSession session = userDetails.get().getSession();  // Get WebSocket session
                    if (session != null && session.isOpen()) {  // Ensure session is valid
                        sendJsonMessage(session, "group", message, sender);
                    }
                }
            }
        }
    }



    private void sendPrivateMessage(String sender, String receiver, String message) throws IOException {
        WebSocketSession receiverSession = users.get(receiver).getSession();
        if (receiverSession != null && receiverSession.isOpen()) {
            sendJsonMessage(receiverSession, "private", message, sender);
        }else{
            WebSocketSession senderSession = users.get(sender).getSession();
            if (senderSession != null && senderSession.isOpen()) {
                sendJsonMessage(receiverSession, "private", "Failed to send Message : Reciever is not active", sender);
            }
        }
    }

    private void sendJsonMessage(WebSocketSession session, String type, String message, String sender) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", type,
                    "sender", sender,
                    "message", message,
                    "timestamp", Instant.now().toString()
            ))));
        }
    }

    private void sendJsonMessage(WebSocketSession session, String type, String message, String sender, HashMap data) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", type,
                    "sender", sender,
                    "message", message,
                    "data", data,
                    "timestamp", Instant.now().toString()
            ))));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String disconnectedUser = null;

        for (Map.Entry<String, UserDetails> entry : users.entrySet()) {
            if (entry.getValue().getSession().equals(session)) {
                disconnectedUser = entry.getKey();
                users.remove(entry.getKey());
                break;
            }
        }

        if (disconnectedUser != null) {
            final String userToRemove = disconnectedUser;
            contactUserMap.values().removeIf(userId -> userId.equals(userToRemove));
            groupMembers.values().forEach(members -> members.remove(userToRemove));

            try {
                sendBroadcastMessage("System", userToRemove + " has left the chat.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    class UserDetails {
        private String username;
        private String useruniqueId;
        private String usercontact;
        private WebSocketSession session;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getUseruniqueId() {
            return useruniqueId;
        }

        public void setUseruniqueId(String useruniqueId) {
            this.useruniqueId = useruniqueId;
        }

        public String getUsercontact() {
            return usercontact;
        }

        public void setUsercontact(String usercontact) {
            this.usercontact = usercontact;
        }

        public WebSocketSession getSession() {
            return session;
        }

        public void setSession(WebSocketSession session) {
            this.session = session;
        }
    }
}
