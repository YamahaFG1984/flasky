package com.example.javalinky.web;

/** 首页的页面模型。name 为 null 表示还没填过表单。 */
public record IndexPage(String name, boolean known) {}
