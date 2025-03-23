package com.employee.service;

import com.employee.model.Message;
import com.employee.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketHandler extends TextWebSocketHandler {


    private static final Map<String, WebSocketSession> users = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> groupMembers = new ConcurrentHashMap<>();
    private static final Map<String, String> groupNames = new ConcurrentHashMap<>();
    private static final Map<String, String> contactUserMap = new ConcurrentHashMap<>();

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebSocketHandler(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = session.getUri().getQuery().replace("token=", "");
        String username = jwtUtil.isTokenValid(token);
        if (username != null) {
            users.put(username, session);
            sendJsonMessage(session, "system", username+" Connected Successfully." , username);
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
        for (WebSocketSession session : users.values()) {
            if (session.isOpen()) {
                sendJsonMessage(session, "broadcast", message, sender);
            }
        }
    }
    private void createGroup(String groupName, String creator, WebSocketSession session) throws IOException {
        String groupId = UUID.randomUUID().toString();
        groupMembers.putIfAbsent(groupId, new HashSet<>());
        groupMembers.get(groupId).add(creator);
        groupNames.put(groupId, groupName);
        sendJsonMessage(session, "system", "Group '" + groupName + "' created successfully. -GroupId : "+groupId, creator);
        sendBroadcastMessage("System", creator + " created the group '" + groupName + "' (ID: " + groupId + ").");
    }


    private void joinGroup(String groupId, String username, WebSocketSession session) throws IOException {
        if (!groupMembers.containsKey(groupId)) {
            sendJsonMessage(session, "error", "Group does not exist!", "system");
            return;
        }

        groupMembers.get(groupId).add(username);
        sendJsonMessage(session, "system", "You have joined the group!", username);
    }

    private void leaveGroup(String groupId, String username, WebSocketSession session) throws IOException {
        if (!groupMembers.containsKey(groupId) || !groupMembers.get(groupId).contains(username)) {
            sendJsonMessage(session, "error", "You are not in this group!", "system");
            return;
        }

        groupMembers.get(groupId).remove(username);
        sendJsonMessage(session, "system", "You have left the group!", username);
        sendGroupMessage("System", groupId, username + " left the group.");
    }

    private void sendGroupMessage(String sender, String groupId, String message) throws IOException {
        if (groupMembers.containsKey(groupId)) {
            for (String member : groupMembers.get(groupId)) {
                WebSocketSession session = users.get(member);
                if (session != null && session.isOpen()) {
                    sendJsonMessage(session, "group", message, sender);
                }
            }
        }
    }

    private void sendPrivateMessage(String sender, String receiver, String message) throws IOException {
        WebSocketSession receiverSession = users.get(receiver);
        if (receiverSession != null && receiverSession.isOpen()) {
            sendJsonMessage(receiverSession, "private", message, sender);
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
    private void sendJsonMessage(WebSocketSession session, String type, String message, String sender,HashMap data) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", type,
                    "sender", sender,
                    "message", message,
                    "data",data,
                    "timestamp", Instant.now().toString()
            ))));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String disconnectedUser = null;

        for (Map.Entry<String, WebSocketSession> entry : users.entrySet()) {
            if (entry.getValue().equals(session)) {
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


    class UserDetails{
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
