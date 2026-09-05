package com.pusula.desktop.dto;

import java.util.List;

public class ServicePhotoPageDTO {
    private List<ServicePhotoDTO> items;
    private int page;
    private int size;
    private long totalServiceFiles;
    private int totalPages;
    private boolean hasNext;

    public List<ServicePhotoDTO> getItems() { return items; }
    public void setItems(List<ServicePhotoDTO> items) { this.items = items; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public long getTotalServiceFiles() { return totalServiceFiles; }
    public void setTotalServiceFiles(long totalServiceFiles) { this.totalServiceFiles = totalServiceFiles; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    public boolean isHasNext() { return hasNext; }
    public void setHasNext(boolean hasNext) { this.hasNext = hasNext; }
}
