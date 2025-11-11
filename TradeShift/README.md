# TradeShift - Financial Portfolio Management & Trading Platform

A secure, high-performance platform for modern investors to track their multi-asset portfolios, analyze market data in real-time, and execute simulated trades, combining the reliability of SQL for transactions with the flexibility of NoSQL for market data.

## Features

### Backend Features
- **Secure Authentication**: JWT-based stateless authentication with Spring Security
- **Transaction Engine**: Atomic buy/sell order processing with strong consistency using SQL
- **Portfolio Management**: Track and manage multi-asset portfolios
- **Market Data Aggregation**: Store and retrieve financial news and historical prices using MongoDB
- **Real-time Price Updates**: WebSocket-based live price streaming
- **Account Management**: Cash balance tracking and fund management

### Frontend Features
- **Served by Spring Boot**: Frontend is served directly from Spring Boot static resources
- **User Registration & Login**: Secure authentication flow
- **Portfolio Dashboard**: View holdings with real-time price updates
- **Trading Interface**: Execute buy and sell orders
- **Transaction History**: View all past transactions
- **Portfolio Charts**: Visual allocation with Chart.js
- **Real-time Updates**: Live price updates via WebSocket

## Technology Stack

### Backend
- Java 21
- Spring Boot 3.5.6
- Spring Security with JWT
- Spring Data JPA (MySQL/PostgreSQL)
- Spring Data MongoDB
- WebSockets (STOMP)
- Maven

### Frontend
- Plain HTML, CSS, and JavaScript (no build step required)
- Chart.js (via CDN) for data visualization
- SockJS & STOMP.js (via CDN) for WebSocket communication
- Modern CSS with dark theme

### Databases
- MySQL (for transactions and portfolios)
- MongoDB (for market data)

## Prerequisites

- Java 21 or higher
- Maven 3.6+
- MySQL 8.0+
- MongoDB 7.0+

## Setup Instructions

### 1. Database Setup

#### MySQL
```bash
# Create database
mysql -u root -p
CREATE DATABASE tradeshift;
```

#### MongoDB
```bash
# MongoDB should be running on localhost:27017
# No manual setup required - Spring Boot will create collections automatically
```

### 2. Backend Setup

```bash
# Navigate to project root
cd TradeShift

# Configure database in application.properties if needed
# Default settings:
# - MySQL: localhost:3306/tradeshift (user: root, password: root)
# - MongoDB: localhost:27017/tradeshift

# Build and run
mvn clean install
mvn spring-boot:run
```

Backend will be available at `http://localhost:8080`

**Note**: Make sure MySQL and MongoDB are running before starting the backend.

### 3. Frontend Setup

**IMPORTANT**: The frontend should NOT be opened directly from the file system (file:// protocol). This will cause CORS errors and connection failures.

#### Option 1: Use Spring Boot's Built-in Server (Recommended)
The frontend is served directly by Spring Boot! No separate server needed.

The frontend files should be located in `src/main/resources/static/`:
- `index.html` - Main HTML file
- `app.js` - JavaScript code

When you run Spring Boot, the frontend will be automatically served at `http://localhost:8080`.

#### Option 2: Use Separate Frontend Server
If you want to run the frontend separately for development:

```bash
# Navigate to frontend directory
cd frontend

# Install dependencies (if not already installed)
npm install

# Start frontend server on port 8081
npm start
```

Then access the frontend at `http://localhost:8081` (it will connect to the API on port 8080).

**Note**: 
- The frontend uses CDN links for Chart.js, SockJS, and STOMP.js, so an internet connection is required for these libraries to load.
- CORS is configured to allow connections from all localhost ports.

## Quick Start

1. **Start MySQL and MongoDB** on your local machine
2. **Start Spring Boot**: `mvn spring-boot:run` (from project root)
3. **Access**: Open `http://localhost:8080` in your browser

That's it! Spring Boot serves both the backend API and the frontend automatically.

## API Endpoints

### Authentication
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login and get JWT token

### Portfolio & Transactions (SQL)
- `GET /api/portfolio` - Get current user's portfolio holdings
- `POST /api/portfolio` - Add asset to portfolio
- `DELETE /api/portfolio/{id}` - Remove asset from portfolio
- `GET /api/transactions` - Get user's transaction history
- `POST /api/trades/buy` - Execute buy order
- `POST /api/trades/sell` - Execute sell order

### Account Management
- `GET /api/account/balance` - Get cash balance
- `POST /api/account/credit` - Add funds to account

### Market Data (MongoDB)
- `GET /api/marketdata/{symbol}` - Get news and historical data for a stock

### Real-time (WebSocket)
- `/ws/prices` - WebSocket endpoint for streaming live price updates
- Subscribe to `/topic/prices/{symbol}` to receive price updates

## Testing

### Run Tests
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=TradingServiceTest
```

### Test Coverage
- Unit tests for services (TradingService, MarketDataService)
- Integration tests for controllers (TradingController, MarketDataController)

## Project Structure

```
TradeShift/
├── src/
│   ├── main/
│   │   ├── java/com/example/TradeShift/
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── model/           # JPA & MongoDB entities
│   │   │   ├── repository/      # Data repositories
│   │   │   ├── service/          # Business logic
│   │   │   ├── security/         # JWT & security config
│   │   │   ├── web/              # Auth endpoints
│   │   │   └── config/           # WebSocket & other configs
│   │   └── resources/
│   │       └── application.properties
│   └── test/                     # Test files
├── src/main/resources/static/
│   ├── index.html               # Frontend HTML (served by Spring Boot)
│   └── app.js                   # Frontend JavaScript
└── pom.xml
```

## Key Modules

### 1. Secure Transaction & Order Processing Engine
- **Location**: `TradingService`, `TradingController`
- **Database**: MySQL (JPA)
- **Features**: Atomic buy/sell operations with balance checks

### 2. Financial News & Data Aggregation
- **Location**: `MarketDataService`, `MarketDataController`
- **Database**: MongoDB
- **Features**: News articles, historical prices, scheduled updates

### 3. Real-time Price Streaming
- **Location**: `WebSocketConfig`, `PriceUpdateService`
- **Protocol**: WebSocket (STOMP)
- **Features**: Live price updates every 5 seconds

## Usage Examples

### Register a User
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","email":"test@example.com","password":"password123"}'
```

### Execute Buy Order
```bash
curl -X POST http://localhost:8080/api/trades/buy \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{"symbol":"AAPL","quantity":10,"price":150.0}'
```

### Get Market Data
```bash
curl http://localhost:8080/api/marketdata/AAPL \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## Development Notes

- The application uses JWT tokens for authentication. Store the token from login/register and include it in the `Authorization` header for protected endpoints.
- Market data is cached in MongoDB for 5 minutes to reduce API calls.
- Real-time price updates are broadcast every 5 seconds to subscribed clients.
- Scheduled tasks update popular symbols every 5 minutes.

## Troubleshooting

### Backend won't start
- Check MySQL is running: `mysql -u root -p`
- Check MongoDB is running: `mongosh`
- Verify ports 8080, 3306, 27017 are available

### Frontend can't connect
- **CRITICAL**: Do NOT open `index.html` directly from the file system (file:// protocol). This will cause CORS errors.
- **Option 1** (Recommended): Access the app directly from Spring Boot at `http://localhost:8080` (Spring Boot serves the frontend automatically)
- **Option 2**: If using the frontend directory separately, run `npm install` then `npm start` from the `frontend/` directory to serve on port 8081
- Verify Spring Boot is running on port 8080
- Check that static files are in `src/main/resources/static/`
- Verify CORS is properly configured (should allow all localhost ports)
- Check browser console for detailed error messages
- Verify WebSocket endpoint is accessible

### Database connection errors
- Verify database credentials in `application.properties`
- Check database is running and accessible
- Ensure MySQL is running on port 3306
- Ensure MongoDB is running on port 27017

## License

This project is for educational purposes.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## Acknowledgments

- Spring Boot team for excellent framework
- Chart.js for visualization library
- Yahoo Finance for market data API (used for demo purposes)

