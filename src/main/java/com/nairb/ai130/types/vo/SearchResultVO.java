package com.nairb.ai130.types.vo;

public class SearchResultVO {
    private String id;
    private String text;
    private double score;
    private String source;

    public SearchResultVO() {}
    public SearchResultVO(String id, String text, double score, String source) {
        this.id = id; this.text = text; this.score = score; this.source = source;
    }
    public String getId() { return id; }
    public String getText() { return text; }
    public double getScore() { return score; }
    public String getSource() { return source; }
}
