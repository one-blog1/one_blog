package com.oneblog.post;

import java.util.List;

/** 상세 화면에서 부가 기능이 채우는 값. */
public class PostView {

    public String categoryName;
    public List<String> tags = List.of();
    public boolean liked;
    /** 이번 요청에서 새로 센 조회수 (014). DB 값은 같은 요청 안에서 다시 읽지 않으므로 더해서 보여준다. */
    public int extraViews;
}
