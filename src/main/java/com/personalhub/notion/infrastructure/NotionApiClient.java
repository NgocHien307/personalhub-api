package com.personalhub.notion.infrastructure;

import com.personalhub.notion.infrastructure.dto.NotionPageDto;
import java.time.Instant;
import java.util.List;

public interface NotionApiClient {
    List<NotionPageDto> query(Instant since);
}
