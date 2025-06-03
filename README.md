# bleapp

bleapp is a basic Java-based BLE (Bluetooth Low Energy) application that receives data as a JSON string from an ESP32 device.

## Features

- Connects to ESP32 via BLE
- Receives and parses JSON data strings
- Displays or processes received data

## Getting Started

### Prerequisites

- Java 8 or higher
- An ESP32 device configured to send JSON strings over BLE

### Installation

1. Clone this repository:
   ```bash
   git clone https://github.com/madhavraj2004/bleapp.git
   ```
2. Navigate to the project directory:
   ```bash
   cd bleapp
   ```
3. Compile the source code:
   ```bash
   javac -d bin src/**/*.java
   ```
   *(Or use your preferred IDE/build tool if applicable)*

### Running the Application

1. Make sure your ESP32 device is powered on and advertising over BLE.
2. Run the application:
   ```bash
   java -cp bin Main
   ```
   *(Replace `Main` with the actual main class name if different)*

3. Follow in-app instructions to connect to your ESP32 device.

## Contributing

Contributions are welcome! Please open issues or submit pull requests.


## Author

- madhavraj2004
