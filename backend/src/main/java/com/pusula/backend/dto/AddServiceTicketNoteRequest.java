package com.pusula.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class AddServiceTicketNoteRequest {
    @NotBlank(message = "Teknisyen notu boş olamaz.")
    private String content;
    private boolean important;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isImportant() { return important; }
    public void setImportant(boolean important) { this.important = important; }
}
