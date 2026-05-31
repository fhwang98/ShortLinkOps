package com.fhwang.shortlinkops.exception;

public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException() {
        super("존재하지 않는 단축 URL입니다.");
    }
}
