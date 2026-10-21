package com.example.javalinky.web;

/** 所有页面都需要的、和布局有关的数据。第 8 章会往里加 currentUser。 */
public record Layout(String title) {

    public static Layout of(String title) {
        return new Layout(title);
    }
}
