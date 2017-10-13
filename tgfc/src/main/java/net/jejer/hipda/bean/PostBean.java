package net.jejer.hipda.bean;

import android.net.Uri;

import java.util.Map;

/**
 * Used for post arguments
 * Created by GreenSkinMonster on 2015-03-14.
 */
public class PostBean {

    private String tid;
    private String pid;
    private String fid;
    private String floor;
    private String page;
    private String score;
    private String reason;
    private String subject;
    private String content;
    private String typeid;
    private Map<Uri, String> images;

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public String getFloor() {
        return floor;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }

    public Map<Uri, String> getImages() { return images; }

    public void setImages(Map<Uri, String> images) {
        this.images = images;
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public String getScore() {
        return score;
    }

    public void setScore(String score) {
        this.score = score;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTypeid() {
        return typeid;
    }

    public void setTypeid(String typeid) {
        this.typeid = typeid;
    }

}
