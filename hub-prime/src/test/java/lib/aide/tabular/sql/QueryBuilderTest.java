package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class QueryBuilderTest {

    private DSLContext dsl;
    private QueryBuilder sqlQueryBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        dsl = DSL.using(SQLDialect.POSTGRES);
        sqlQueryBuilder = new QueryBuilder(dsl);
    }

    @Test
    public void testSimpleSelect() throws Exception {
        String jsonRequest = """
                    {
                        "startRow": 0,
                        "endRow": 10,
                        "rowGroupCols": [],
                        "valueCols": [{"field": "region"}, {"field": "sales"}],
                        "pivotMode": false,
                        "groupKeys": [],
                        "filterModel": {},
                        "sortModel": []
                    }
                """;
        var request = objectMapper.readValue(jsonRequest, EnterpriseGetRowsRequest.class);

        // Verify the deserialized request object
        assertThat(request.getValueCols()).hasSize(2);
        assertThat(request.getValueCols().get(0).getField()).isEqualTo("region");
        assertThat(request.getValueCols().get(1).getField()).isEqualTo("sales");

        var query = sqlQueryBuilder.createSql(request, "sales_data", Map.of());
        var expected = """
                    SELECT "region", "sales"
                    FROM "sales_data"
                    OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """.stripIndent();

        assertThat(query.getSQL()).isEqualToIgnoringWhitespace(expected);

        // Verify bind values
        List<Object> expectedBindValues = List.of(0L, 11L);
        assertThat(query.getBindValues()).containsExactlyElementsOf(expectedBindValues);
    }

    @Test
    public void testSimpleSelectWithSorting() throws Exception {
        String jsonRequest = """
                    {
                        "startRow": 0,
                        "endRow": 10,
                        "rowGroupCols": [],
                        "valueCols": [{"field": "region"}, {"field": "sales"}],
                        "pivotMode": false,
                        "groupKeys": [],
                        "filterModel": {},
                        "sortModel": [{"colId": "region", "sort": "asc"}, {"colId": "sales", "sort": "desc"}]
                    }
                """;
        var request = objectMapper.readValue(jsonRequest, EnterpriseGetRowsRequest.class);

        // Verify the deserialized request object
        assertThat(request.getValueCols()).hasSize(2);
        assertThat(request.getValueCols().get(0).getField()).isEqualTo("region");
        assertThat(request.getValueCols().get(1).getField()).isEqualTo("sales");
        assertThat(request.getSortModel()).hasSize(2);
        assertThat(request.getSortModel().get(0).getColId()).isEqualTo("region");
        assertThat(request.getSortModel().get(0).getSort()).isEqualTo("asc");
        assertThat(request.getSortModel().get(1).getColId()).isEqualTo("sales");
        assertThat(request.getSortModel().get(1).getSort()).isEqualTo("desc");

        var query = sqlQueryBuilder.createSql(request, "sales_data", Map.of());
        var expected = """
                    SELECT "region", "sales"
                    FROM "sales_data"
                    ORDER BY "region" ASC, "sales" DESC
                    OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """.stripIndent();

        assertThat(query.getSQL()).isEqualToIgnoringWhitespace(expected);

        // Verify bind values
        List<Object> expectedBindValues = List.of(0L, 11L);
        assertThat(query.getBindValues()).containsExactlyElementsOf(expectedBindValues);
    }

    @Test
    public void testSelectWithSortingAndFiltering() throws Exception {
        String jsonRequest = """
                    {
                        "startRow": 0,
                        "endRow": 10,
                        "rowGroupCols": [],
                        "valueCols": [{"field": "region"}, {"field": "sales"}],
                        "pivotMode": false,
                        "groupKeys": [],
                        "filterModel": {"region": {"filterType": "text", "type": "equals", "filter": "North"}},
                        "sortModel": [{"colId": "region", "sort": "asc"}, {"colId": "sales", "sort": "desc"}]
                    }
                """;
        var request = objectMapper.readValue(jsonRequest, EnterpriseGetRowsRequest.class);

        // Verify the deserialized request object
        assertThat(request.getValueCols()).hasSize(2);
        assertThat(request.getValueCols().get(0).getField()).isEqualTo("region");
        assertThat(request.getValueCols().get(1).getField()).isEqualTo("sales");
        assertThat(request.getSortModel()).hasSize(2);
        assertThat(request.getSortModel().get(0).getColId()).isEqualTo("region");
        assertThat(request.getSortModel().get(0).getSort()).isEqualTo("asc");
        assertThat(request.getSortModel().get(1).getColId()).isEqualTo("sales");
        assertThat(request.getSortModel().get(1).getSort()).isEqualTo("desc");
        assertThat(((TextColumnFilter) request.getFilterModel().get("region")).getFilter()).isEqualTo("North");

        var query = sqlQueryBuilder.createSql(request, "sales_data", Map.of());
        var expected = """
                    SELECT "region", "sales"
                    FROM "sales_data"
                    WHERE "region" = ?
                    ORDER BY "region" ASC, "sales" DESC
                    OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """.stripIndent();

        assertThat(query.getSQL()).isEqualToIgnoringWhitespace(expected);

        // Verify bind values
        List<Object> expectedBindValues = List.of("North", 0L, 11L);
        assertThat(query.getBindValues()).containsExactlyElementsOf(expectedBindValues);
    }

    @Test
    public void testSelectWithSortingFilteringAndGrouping() throws Exception {
        String jsonRequest = """
                    {
                        "startRow": 0,
                        "endRow": 10,
                        "rowGroupCols": [{"field": "region"}],
                        "valueCols": [{"aggFunc": "sum", "field": "sales"}],
                        "pivotMode": false,
                        "groupKeys": ["region"],
                        "filterModel": {"sales": {"filterType": "number", "type": "greaterThan", "filter": 1000}},
                        "sortModel": [{"colId": "region", "sort": "asc"}]
                    }
                """;
        var request = objectMapper.readValue(jsonRequest, EnterpriseGetRowsRequest.class);

        // Verify the deserialized request object
        assertThat(request.getRowGroupCols()).hasSize(1);
        assertThat(request.getRowGroupCols().get(0).getField()).isEqualTo("region");
        assertThat(request.getValueCols()).hasSize(1);
        assertThat(request.getValueCols().get(0).getAggFunc()).isEqualTo("sum");
        assertThat(request.getValueCols().get(0).getField()).isEqualTo("sales");
        assertThat(request.getGroupKeys()).containsExactly("region");
        assertThat(((NumberColumnFilter) request.getFilterModel().get("sales")).getFilter()).isEqualTo(1000);
        assertThat(request.getSortModel()).hasSize(1);
        assertThat(request.getSortModel().get(0).getColId()).isEqualTo("region");
        assertThat(request.getSortModel().get(0).getSort()).isEqualTo("asc");

        var query = sqlQueryBuilder.createSql(request, "sales_data", Map.of());
        var expected = """
                    SELECT region, sum("sales") AS "sales"
                    FROM "sales_data"
                    WHERE ("region" = ? AND "sales" > ?)
                    GROUP BY region
                    ORDER BY "region" ASC
                    OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """.stripIndent();

        assertThat(query.getSQL()).isEqualToIgnoringWhitespace(expected);

        // Verify bind values
        List<Object> expectedBindValues = List.of("region", 1000, 0L, 11L);
        assertThat(query.getBindValues()).containsExactlyElementsOf(expectedBindValues);
    }

    @Test
    public void testSelectWithPivoting() throws Exception {
        String jsonRequest = """
            {
                "startRow": 0,
                "endRow": 10,
                "rowGroupCols": [{"field": "region"}],
                "valueCols": [{"aggFunc": "sum", "field": "sales"}],
                "pivotCols": [{"field": "quarter"}],
                "pivotMode": true,
                "groupKeys": ["region"],
                "filterModel": {},
                "sortModel": [{"colId": "region", "sort": "asc"}]
            }
        """;

        var request = objectMapper.readValue(jsonRequest, EnterpriseGetRowsRequest.class);
        var pivotValues = Map.of("quarter", List.of("Q1", "Q2", "Q3", "Q4"));

        // Verify request parsing
        assertThat(request.getStartRow()).isEqualTo(0);
        assertThat(request.getEndRow()).isEqualTo(10);
        assertThat(request.getRowGroupCols()).hasSize(1);
        assertThat(request.getRowGroupCols().get(0).getField()).isEqualTo("region");
        assertThat(request.getValueCols()).hasSize(1);
        assertThat(request.getValueCols().get(0).getAggFunc()).isEqualTo("sum");
        assertThat(request.getValueCols().get(0).getField()).isEqualTo("sales");
        assertThat(request.getPivotCols()).hasSize(1);
        assertThat(request.getPivotCols().get(0).getField()).isEqualTo("quarter");
        assertThat(request.isPivotMode()).isTrue();
        assertThat(request.getGroupKeys()).containsExactly("region");
        assertThat(request.getFilterModel()).isEmpty();
        assertThat(request.getSortModel()).hasSize(1);
        assertThat(request.getSortModel().get(0).getColId()).isEqualTo("region");
        assertThat(request.getSortModel().get(0).getSort()).isEqualTo("asc");

        var query = sqlQueryBuilder.createSql(request, "sales_data", pivotValues);

        // Verify SQL generation
        String generatedSql = query.getSQL().replaceAll("\\s+", " ").trim();

        assertThat(generatedSql).contains("SELECT");
        assertThat(generatedSql).contains("region,");
        assertThat(generatedSql).containsPattern("SUM\\(CASE WHEN .*quarter.* = \\? THEN .*sales.* ELSE CAST\\(\\? AS int\\) END\\) AS .*quarter_Q1_sales.*");
        assertThat(generatedSql).containsPattern("SUM\\(CASE WHEN .*quarter.* = \\? THEN .*sales.* ELSE \\? END\\) AS .*quarter_Q2_sales.*");
        assertThat(generatedSql).containsPattern("SUM\\(CASE WHEN .*quarter.* = \\? THEN .*sales.* ELSE \\? END\\) AS .*quarter_Q3_sales.*");
        assertThat(generatedSql).containsPattern("SUM\\(CASE WHEN .*quarter.* = \\? THEN .*sales.* ELSE \\? END\\) AS .*quarter_Q4_sales.*");
        assertThat(generatedSql).contains("FROM \"sales_data\"");
        assertThat(generatedSql).containsPattern("WHERE .*region.* = \\?");
        assertThat(generatedSql).containsPattern("GROUP BY .*region.*");
        assertThat(generatedSql).containsPattern("ORDER BY .*region.* ASC");
        assertThat(generatedSql).contains("OFFSET ? ROWS");
        assertThat(generatedSql).contains("FETCH NEXT ? ROWS ONLY");

        // Verify bind values
        List<Object> expectedBindValues = Arrays.asList("region", 0, "11", null, "Q3", null, "Q4", null, "region", 0L, 11L);
        assertThat(query.getBindValues()).containsExactlyElementsOf(expectedBindValues);

        // Additional checks for bind values
        assertThat(query.getBindValues()).hasSize(11);
        assertThat(query.getBindValues().get(0)).isEqualTo("region");
        assertThat(query.getBindValues().get(1)).isEqualTo(0);
        assertThat(query.getBindValues().get(2)).isEqualTo("11");
        assertThat(query.getBindValues().get(3)).isNull();
        assertThat(query.getBindValues().get(4)).isEqualTo("Q3");
        assertThat(query.getBindValues().get(5)).isNull();
        assertThat(query.getBindValues().get(6)).isEqualTo("Q4");
        assertThat(query.getBindValues().get(7)).isNull();
        assertThat(query.getBindValues().get(8)).isEqualTo("region");
        assertThat(query.getBindValues().get(9)).isEqualTo(0L);
        assertThat(query.getBindValues().get(10)).isEqualTo(11L);
    }
}









