# MCP Server Template

A Spring Boot template for creating Model Context Protocol (MCP) servers using Spring AI.

## Overview

This template provides a clean starting point for building MCP servers with Spring Boot. It includes:

- Basic MCP server configuration using Spring AI
- Example tool implementation
- Simple web chat interface for testing
- Maven build configuration

## Features

- **MCP Protocol Support**: Built on Spring AI's MCP server implementation
- **Example Tool**: Echo tool that demonstrates the MCP tool pattern
- **Web Interface**: Simple chat UI for testing your MCP server
- **Spring Boot**: Production-ready framework with auto-configuration

## Quick Start

### Prerequisites

- Java 21 or later
- Maven 3.6 or later

### Running the Server

1. Clone this repository
2. Run the application:
   ```bash
   mvn spring-boot:run
   ```
3. Access the server:
   - **Chat Interface:** `http://localhost:8080/` 
   - **MCP Server Endpoint:** `http://localhost:8080/mcp`

### Building

```bash
mvn clean package
```

## Configuration

The server is configured in `src/main/resources/application.properties`:

```properties
# Server Configuration
server.port=8080

# Spring AI MCP Server Properties
spring.ai.mcp.server.name=template-mcp-server
spring.ai.mcp.server.version=1.0.0
spring.ai.mcp.server.type=SYNC
spring.ai.mcp.server.instructions=This is a template MCP server with example tools
spring.ai.mcp.server.base-url=/mcp
spring.ai.mcp.server.capabilities.tool=true
spring.ai.mcp.server.capabilities.resource=true
spring.ai.mcp.server.capabilities.prompt=true
spring.ai.mcp.server.capabilities.completion=true
spring.ai.mcp.server.enabled=true
spring.ai.mcp.server.protocol=STATELESS
```

## Adding New Tools

To add new MCP tools, create methods in `ToolService.java` annotated with `@Tool`:

```java
@Tool(description = "Your tool description")
public String yourTool(String parameter) {
    // Your tool implementation
    return "Tool result";
}
```

## Project Structure

```
src/
├── main/
│   ├── java/com/example/mcpserver/
│   │   ├── McpServerApplication.java      # Main application class
│   │   ├── controller/
│   │   │   └── HomeController.java        # Web controller for chat interface
│   │   └── service/
│   │       └── ToolService.java           # MCP tools implementation
│   └── resources/
│       ├── application.properties         # Application configuration
│       └── static/
│           └── index.html                # Chat interface
```

## Development

### Testing Tools

1. Start the server
2. Navigate to `http://localhost:8080` for the chat interface
3. Use the chat interface to test your tools
4. The example echo tool can be tested by typing any message
5. For MCP client integration, connect to `http://localhost:8080/mcp`

### Customization

1. **Update package names**: Change `com.example` to your desired package structure
2. **Modify server metadata**: Update the MCP server properties in `application.properties`
3. **Add your tools**: Implement new tools in `ToolService.java`
4. **Customize the UI**: Modify `index.html` to match your branding

## License

This template is provided as-is for educational and development purposes.