package com.example.javalinky.web;

import java.util.List;

public record Layout(String title, String csrfToken, List<Flash> flashes) {}
