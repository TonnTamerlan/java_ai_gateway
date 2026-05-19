package com.typedgoose.calc.db;

import com.typedgoose.calc.domain.FileStatus;
import com.typedgoose.calc.domain.SummarizationFile;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class FilesRepositoryImpl implements FilesRepositoryCustom {

    private final JdbcAggregateTemplate template;

    @Override
    public Page<SummarizationFile> search(FileStatus status, String nameSubstring, Pageable pageable) {
        Criteria criteria = Criteria.where("deletedAt").isNull();
        if (status != null) {
            criteria = criteria.and("status").is(status.name());
        }
        if (nameSubstring != null && !nameSubstring.isBlank()) {
            criteria = criteria.and("fileName").like("%" + nameSubstring + "%").ignoreCase(true);
        }

        Query baseQuery = Query.query(criteria);
        long total = template.count(baseQuery, SummarizationFile.class);

        Query pagedQuery = baseQuery.with(pageable);
		List<SummarizationFile> rows = new ArrayList<>(template.findAll(pagedQuery, SummarizationFile.class));

        return new PageImpl<>(rows, pageable, total);
    }
}
