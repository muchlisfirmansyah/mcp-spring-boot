package com.example.mcpserver.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class ToolService {

    @Tool(description = "Example tool that echoes the input message")
    public String exampleTool(String message) {
        return "Echo: " + message;
    }
}