/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.fetch.subphase;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.index.mapper.ParentFieldMapper;
import org.elasticsearch.search.fetch.FetchSubPhase;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ParentFieldSubFetchPhase implements FetchSubPhase {

    @Override
    public void hitExecute(SearchContext context, HitContext hitContext) {
        if (context.storedFieldsContext() != null && context.storedFieldsContext().fetchFields() == false) {
            return ;
        }
        ParentFieldMapper parentFieldMapper = context.mapperService().documentMapper(hitContext.hit().getType()).parentFieldMapper();
        if (parentFieldMapper.active() == false) {
            return;
        }

        String parentId = getParentId(parentFieldMapper, hitContext.reader(), hitContext.docId());
        if (parentId == null) {
            // hit has no _parent field. Can happen for nested inner hits if parent hit is a p/c document.
            return;
        }

        Map<String, DocumentField> fields = hitContext.hit().fieldsOrNull();
        if (fields == null) {
            fields = new HashMap<>();
            hitContext.hit().fields(fields);
        }
        fields.put(ParentFieldMapper.NAME, new DocumentField(ParentFieldMapper.NAME, Collections.singletonList(parentId)));
    }

    public static String getParentId(ParentFieldMapper fieldMapper, LeafReader reader, int docId) {
        try {
            SortedDocValues docValues = reader.getSortedDocValues(fieldMapper.name());
            if (docValues == null || docValues.advanceExact(docId) == false) {
                // hit has no _parent field.
                return null;
            }
            BytesRef parentId = docValues.binaryValue();
            return parentId.length > 0 ? parentId.utf8ToString() : null;
        } catch (IOException e) {
            throw ExceptionsHelper.convertToElastic(e);
        }
    }

}
