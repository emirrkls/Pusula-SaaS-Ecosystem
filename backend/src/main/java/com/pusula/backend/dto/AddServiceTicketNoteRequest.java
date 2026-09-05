package com.pusula.backend.dto;

public class AddServiceTicketNoteRequest {
    private String content;
    private boolean important;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isImportant() { return important; }
    public void setImportant(boolean important) { this.important = important; }
}
