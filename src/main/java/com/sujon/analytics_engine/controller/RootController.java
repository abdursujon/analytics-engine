package com.sujon.analytics_engine.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Confirms the service is running.
 */
@RestController
public class RootController {
    @GetMapping(value = "/", produces = "text/html")
    public String home() {
        return """
                <!DOCTYPE html>
                <html>
                  <head>
                    <title>Data Analytics Engine</title>
                    <style>
                      body {
                        margin: 0;
                        height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        background: #0f172a;
                        color: #e5e7eb;
                        font-family: Arial, Helvetica, sans-serif;
                      }
                      .card {
                        padding: 32px 40px;
                        border-radius: 12px;
                        background: #020617;
                        box-shadow: 0 20px 40px rgba(0,0,0,0.6);
                        text-align: center;
                      }
                      h1 {
                        margin: 0 0 12px;
                        font-size: 28px;
                        color: #38bdf8;
                      }
                      p {
                        margin: 6px 0;
                        font-size: 14px;
                        opacity: 0.9;
                      }
                      .status {
                        color:green;
                      }
                    </style>
                  </head>
                  <body>
                    <div class="card">
                      <h1>Spring Data Analysis API</h1>
                      <p class="status">Status: Running</p>
                      <p>Author: Abdur Sujon</p>
                      <p>© 2025</p>
                    </div>
                  </body>
                </html>
                """;
    }
}