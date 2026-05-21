package com.nairb.ai130.domain.service;

import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;

import java.util.List;

/** 文档读取策略接口。每种文档格式实现一个 reader。 */
@FunctionalInterface
public interface DocumentReader {
    List<Document> read(Resource resource);
}
