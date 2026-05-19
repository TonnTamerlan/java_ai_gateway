package com.typedgoose.calc.db;

import com.typedgoose.calc.domain.FileStatus;
import com.typedgoose.calc.domain.SummarizationFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FilesRepositoryCustom {

    Page<SummarizationFile> search(FileStatus status, String nameSubstring, Pageable pageable);
}
