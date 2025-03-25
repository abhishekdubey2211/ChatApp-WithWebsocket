# WebSocket Application Documentation

## Step 1: User Authentication & Token Generation

### Request URL:
```
POST http://localhost:8080/api/auth/login?username=<USERNAME>&contact=<CONTACT>
```

### Example Request 1:
```
POST http://localhost:8080/api/auth/login?username=sachin&contact=8850014998
```

### Example Response:
```json
{
  "useruniqueid": "3f3e76ff-bac7-4904-a2e8-02f22c2c23ad",
  "contact": "8850014998",
  "token": "<JWT_TOKEN>"
}
```

### Example Request 2:
```
POST http://localhost:8080/api/auth/login?username=abhishek&contact=8850014998
```

### Example Response:
```json
{
  "useruniqueid": "82fd38fb-37f3-412b-9e99-503249caca13",
  "contact": "8850014998",
  "token": "<JWT_TOKEN>"
}
```

## Step 2: Connecting to WebSocket

Use the JWT token received in Step 1 to connect to WebSocket.

### WebSocket URL:
```
ws://localhost:8080/ws/chat?token=<JWT_TOKEN>
```

## Step 3: WebSocket Message Formats

### Sending a Private Message
```json
{
  "type": "private",
  "sender": "abhishek",
  "receiver": "sachin",
  "content": "mera mood no=i kaam karne ka"
}
```

### Creating a Group
```json
{
  "type": "create_group",
  "sender": "abhishek",
  "receiver": "sachin",
  "group": "Java Developers"
}
```

### Joining a Group
```json
{
  "type": "join_group",
  "sender": "sachin",
  "group": "456f0046-1522-4e07-9060-bbe596de1746"
}
```

### Sending a Message in a Group
```json
{
  "type": "group",
  "sender": "sachin",
  "group": "72823434-06ea-42a6-9966-c1a2f37bf8f1",
  "content": "Hii Team"
}
```

### Leaving a Group
```json
{
  "type": "leave_group",
  "sender": "sachin",
  "group": "72823434-06ea-42a6-9966-c1a2f37bf8f1",
  "content": "Hii Team"
}
```

### Broadcasting a Message
```json
{
  "type": "broadcast",
  "sender": "sachin",
  "content": "Hii Team from April month we will have holidays for all Saturday and Sunday"
}
```

### Retrieving All Users
```json
{
  "type": "users"
}
```

Footer
