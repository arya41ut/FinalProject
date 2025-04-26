# Restaurant Roulette

This mobile application seeks to take the guesswork out of choosing where to eat. Users can spin restaurant selections in a fun game, and then vote on their preference after a restaurant is randomly selected. If selected, the users can add it to their calendar if they wish.

## Project Structure

This repository contains:

- `app/` - The Android mobile application (Kotlin)
- `demo/` - The Spring Boot backend server application (Java)

## Features

- Room creation and joining via invite codes
- Real-time participant tracking
- Restaurant suggestion collection
- AI-assisted restaurant suggestions (requires OpenAI API key)
- Interactive spinning wheel for final selection
- Voting system for group consensus
- WebSocket communication for real-time updates

## Setup Instructions

### Backend (Spring Boot)

1. Navigate to the `demo/` directory
2. Configure your database settings in `src/main/resources/application.properties`
3.To use the AI suggestion feature, you need to set your own OpenAI API key:
   - In `application.properties`, replace `${OPENAI_API_KEY}` with your actual key
   - Alternatively, set the environment variable `OPENAI_API_KEY` in your deployment environment
4. Run the Spring Boot application:
   ```
   ./mvnw spring-boot:run
   ```



## Note

Without configuring an OpenAI API key, the AI-powered restaurant suggestion feature will not work, but all other features of the application will function normally.

