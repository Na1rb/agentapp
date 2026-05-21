package com.nairb.ai130.infrastructure.reader;

import com.nairb.ai130.domain.service.DocumentReader;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Qualifier("tikaReader")
public class TikaDocumentReaderAdapter implements DocumentReader {
    @Override
    public List<Document> read(Resource resource) {
        return new org.springframework.ai.reader.tika.TikaDocumentReader(resource).read();
    }
}
