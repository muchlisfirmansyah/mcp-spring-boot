package com.example.mcpserver.controller;

import lombok.extern.log4j.Log4j;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Log4j2
@RestController
@RequestMapping("/")
public class HomeController {

    @Value("${chat.webhook.url}")
    private String chatWebhookUrl;

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public String home() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/index.html");
        byte[] bdata = FileCopyUtils.copyToByteArray(resource.getInputStream());
        String html = new String(bdata, StandardCharsets.UTF_8);
        
        // Replace placeholder with actual property value
        html = html.replace("{{CHAT_WEBHOOK_URL}}", chatWebhookUrl);
        log.info("Serving home page with chatWebhookUrl: {{CHAT_WEBHOOK_URL}}" + chatWebhookUrl);
        return html;
    }
}