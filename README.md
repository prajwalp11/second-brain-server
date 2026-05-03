# Second Brain 🧠

> **Track everything. Setup nothing.**

Second Brain is an intelligent personal tracking system that helps you monitor and improve across all areas of your life—gym, running, learning, hobbies, and more. Unlike traditional tracking apps that force you into rigid templates, Second Brain adapts to *you*.

## 🎯 The Vision

Your brain is amazing at thinking, creating, and problem-solving. But it's terrible at remembering everything you did, tracking your progress, and spotting patterns. That's where Second Brain comes in.

**Second Brain is your AI-powered memory system that:**
- 📝 Logs your activities through natural language (just talk to it)
- 📊 Tracks metrics that matter to you (not generic templates)
- 🎯 Sets and monitors goals automatically
- 🔥 Maintains streaks and celebrates wins
- 💡 Discovers patterns you'd never notice
- 🚀 Suggests what to do next based on your data

### The Core Philosophy

1. **Zero Setup Friction**: Tell the AI what you want to track. It figures out the rest.
2. **Natural Logging**: "Benched 225 for 5x3" → Automatically parsed and logged
3. **Intelligent Insights**: AI analyzes your data to find patterns and suggest improvements
4. **Flexible Domains**: Track anything—fitness, learning, creative projects, habits
5. **Your Data, Your Way**: Customize metrics, goals, and tracking methods

## 🏗️ Architecture

### Backend (This Repository)
- **Framework**: Spring Boot 3.x
- **Database**: PostgreSQL
- **AI Integration**: Google Gemini API
- **Authentication**: JWT + Google OAuth

### Key Features

#### 🤖 AI-Powered System
- **Natural Language Logging**: Parse free-form text into structured data
- **Smart Domain Setup**: AI generates metrics, milestones, and tasks based on your goals
- **Personalized Insights**: Weekly highlights, pattern detection, and suggestions
- **Context-Aware Chat**: Conversational interface that understands your history

#### 📊 Flexible Tracking
- **Domains**: Organize your life into areas (Gym, Running, Guitar, etc.)
- **Custom Metrics**: Track what matters to you (weight, reps, distance, practice time)
- **Personal Records**: Automatic PR detection and celebration
- **Milestones**: Set goals and track progress automatically

#### 📈 Analytics & Insights
- **Time Series Charts**: Visualize progress over time
- **Streak Tracking**: Build consistency with daily/weekly streaks
- **Weekly Stats**: Compare performance week-over-week
- **AI Insights**: Discover patterns and get actionable suggestions

#### 🎯 Goal Management
- **Smart Milestones**: AI suggests realistic goals based on your data
- **Progress Tracking**: Automatic calculation of milestone completion
- **Task Management**: Break down goals into actionable tasks

## 🚀 Getting Started

### Prerequisites
- Java 17+
- PostgreSQL 14+
- Google Gemini API Key

### Installation

1. **Clone the repository**
```bash
git clone https://github.com/yourusername/second-brain-server.git
cd second-brain-server
```

2. **Set up PostgreSQL**
```bash
createdb secondbrain_db
```

3. **Configure environment variables**
```bash
# application.properties
spring.datasource.url=jdbc:postgresql://localhost:5432/secondbrain_db
spring.datasource.username=your_username
spring.datasource.password=your_password

# Gemini API
gemini.api.key=YOUR_GEMINI_API_KEY

# JWT Secret (change in production!)
jwt.secret=your-secret-key-here
jwt.access-token-expiry-ms=86400000

# Google OAuth (optional)
google.client.id=your-google-client-id
```

4. **Run the application**
```bash
./gradlew bootRun
```

The server will start at `http://localhost:8080`

### Database Setup

The application uses Flyway for database migrations. On first run, it will automatically create all tables.

If you have an existing database, the schema is in:
```
src/main/resources/db/V1__create_initial_schema.sql
```

## 📡 API Overview

### Authentication
```bash
# Register
POST /api/auth/register
{
  "email": "user@example.com",
  "password": "password",
  "name": "John Doe"
}

# Login
POST /api/auth/login
{
  "email": "user@example.com",
  "password": "password"
}
```

### Domains
```bash
# Create domain (AI generates metrics/milestones)
POST /api/domains
{
  "domainType": "GYM",
  "skillLevel": "INTERMEDIATE",
  "linkedResourceUrl": "https://example.com/workout-plan"
}

# Get all domains
GET /api/domains

# Get chart data
GET /api/domains/{id}/chart-data?days=30
```

### Session Logging
```bash
# Natural language logging
POST /api/ai/chat
{
  "message": "Benched 225 for 5 reps, 3 sets"
}

# Manual logging
POST /api/sessions
{
  "domainId": "uuid",
  "logDate": "2026-05-03",
  "feel": "GOOD",
  "notes": "Great workout",
  "metrics": {
    "weight": 225,
    "reps": 5,
    "sets": 3
  }
}
```

### Insights
```bash
# Get AI-generated insights
GET /api/insights

# Response:
{
  "highlights": [
    "7-day streak across all domains!",
    "Completed 2 milestones in Gym"
  ],
  "patterns": [
    "You lift heavier on days you run less"
  ],
  "suggestions": [
    "You haven't logged Guitar in 4 days. Ready to get back?"
  ]
}
```

### Milestones
```bash
# Create milestone
POST /api/milestones
{
  "domainId": "uuid",
  "label": "Bench 300 lbs",
  "metricKey": "weight",
  "targetValue": 300,
  "deadline": "2026-12-31"
}

# Get milestones for domain
GET /api/milestones/{domainId}
```

### Metrics
```bash
# Get metrics for domain
GET /api/metrics/domain/{domainId}

# Reorder metrics
PUT /api/metrics/domain/{domainId}/reorder
["metric-uuid-1", "metric-uuid-2", "metric-uuid-3"]
```

## 🏛️ Project Structure

```
src/main/java/com/secondbrain/second_brain_server/
├── config/              # Spring configuration
├── controllers/         # REST API endpoints
├── dto/                 # Data Transfer Objects
│   ├── request/        # API request models
│   └── response/       # API response models
├── entities/           # JPA entities (database models)
├── enums/              # Enums (status, types, etc.)
├── exception/          # Custom exceptions & error handling
├── external/           # External API clients (Gemini)
├── repository/         # JPA repositories
├── security/           # JWT & authentication
├── service/            # Business logic
│   └── ai/            # AI-related services
├── services/           # Additional services
├── scheduler/          # Scheduled tasks (nudges, streaks)
└── util/               # Utility classes
```

## 🧪 Testing

```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport
```

## 🔒 Security

- **JWT Authentication**: Secure token-based auth
- **Password Hashing**: BCrypt with salt
- **CORS Configuration**: Configurable allowed origins
- **Input Validation**: Jakarta Validation on all endpoints
- **SQL Injection Protection**: JPA/Hibernate parameterized queries

## 🌟 Key Technologies

- **Spring Boot 3.x**: Modern Java framework
- **Spring Security**: Authentication & authorization
- **Spring Data JPA**: Database access
- **PostgreSQL**: Relational database
- **Lombok**: Reduce boilerplate code
- **Google Gemini**: AI-powered features
- **Jackson**: JSON processing
- **Gradle**: Build automation

## 📊 Database Schema

### Core Tables
- **users**: User accounts
- **domains**: Tracking areas (Gym, Running, etc.)
- **domain_metric_definitions**: Custom metrics per domain
- **session_logs**: Activity logs
- **session_metric_values**: Metric values per session
- **personal_records**: PR tracking
- **milestones**: Goals and targets
- **tasks**: Action items
- **ai_conversations**: Chat history
- **ai_nudges**: Scheduled reminders

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Google Gemini for AI capabilities
- Spring Boot community for excellent documentation
- Everyone who believes in building a better way to track personal growth

## 📧 Contact

For questions, suggestions, or feedback:
- Email: your.email@example.com
- GitHub Issues: [Create an issue](https://github.com/yourusername/second-brain-server/issues)

---

**Built with ❤️ to help you become the best version of yourself.**

*"Your brain is for having ideas, not holding them." - David Allen*
