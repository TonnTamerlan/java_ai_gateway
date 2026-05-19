package com.typedgoose.contracts.ai;

import java.util.List;

public interface BatchProvider {

    BatchHandle submit(BatchSubmission submission);

    BatchStatus poll(BatchHandle handle);

    List<BatchResult> fetchResults(BatchHandle handle);
}
