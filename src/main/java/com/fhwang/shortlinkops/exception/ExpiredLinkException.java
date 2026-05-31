package com.fhwang.shortlinkops.exception;

public class ExpiredLinkException extends RuntimeException {

    public ExpiredLinkException() {
        super("만료된 단축 URL입니다.");
    }
}
