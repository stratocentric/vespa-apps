// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.example.cord19.searcher;

import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class RelatedArticlesByNNSearcherTest {

    private final String titleNNItem =
            "NEAREST_NEIGHBOR {field=title_embedding,queryTensorName=title_vector,hnsw.exploreAdditionalHits=0,distanceThreshold=Infinity,approximate=true,targetHits=100}";

    private final String abstractNNItem =
            "NEAREST_NEIGHBOR {field=abstract_embedding,queryTensorName=abstract_vector,hnsw.exploreAdditionalHits=0,distanceThreshold=Infinity,approximate=true,targetHits=100}";

    private final String specterNNItem =
            "NEAREST_NEIGHBOR {field=specter_embedding,queryTensorName=specter_vector,hnsw.exploreAdditionalHits=0,distanceThreshold=Infinity,approximate=true,targetHits=100}";

    @Test
    public void testNoopIfNoRelated_to() {
        Query original = new Query("?query=foo%20bar").clone();
        Result result = execute(original, new RelatedArticlesByNNSearcher(), new MockBackend());
        assertEquals(original, result.getQuery());
    }

    @Test
    public void testRelatedToTitleOnly() {
        Query query = new Query("?query=covid-19+%2B%22south+korea%22+%2Brelated_to:123&type=any&use-abstract=false");
        Result result = execute(query, new RelatedArticlesByNNSearcher(), new MockBackend());
        assertEquals("+(AND (RANK (AND \"south korea\") (AND covid 19)) " + titleNNItem + ") -id:123",
                     result.getQuery().getModel().getQueryTree().toString());
    }

    @Test
    public void testRelatedToTitleAndAbstract() {
        Query query = new Query("?query=covid-19+%2B%22south+korea%22+%2Brelated_to:123&type=any&use-abstract=true");
        Result result = execute(query, new RelatedArticlesByNNSearcher(), new MockBackend());
        assertEquals("+(AND (RANK (AND \"south korea\") (AND covid 19)) (OR " + abstractNNItem + " " + titleNNItem + ")) -id:123",
                     result.getQuery().getModel().getQueryTree().toString());
    }

    @Test
    public void testRelatedToTitleAndAbstractAndFilter() {
        Query query = new Query("?query=%2Brelated_to:123&type=any&use-abstract=true&filter=%2Bsource:medrxiv");
        Result result = execute(query, new RelatedArticlesByNNSearcher(), new MockBackend());
        assertEquals("+(AND (AND |source:medrxiv) (OR " + abstractNNItem + " " + titleNNItem + ")) -id:123",
                result.getQuery().getModel().getQueryTree().toString());
    }

    @Test
    public void testRelatedToTitleAndAbstractAndFilterWithQueryTerm() {
        Query query = new Query("?query=%2BCovid-19+%2Brelated_to:123&type=any&use-abstract=true&filter=%2Bsource:medrxiv");
        Result result = execute(query, new RelatedArticlesByNNSearcher(), new MockBackend());
        assertEquals("+(AND (AND Covid 19 |source:medrxiv) (OR " + abstractNNItem + " " + titleNNItem + ")) -id:123",
                result.getQuery().getModel().getQueryTree().toString());
    }

    @Test
    public void testRelatedUsingSpecter() {
        Query query = new Query("?query=covid-19+%2B%22south+korea%22+%2Brelated_to:123&type=any&use-specter&ranking=bm25");
        Result result = execute(query, new RelatedArticlesByNNSearcher(), new MockBackend());
        assertEquals("+(AND (RANK (AND \"south korea\") (AND covid 19)) " + specterNNItem + ") -id:123",
                result.getQuery().getModel().getQueryTree().toString());
        assertEquals(query.getRanking().getProfile(), "bm25");
    }

    @Test
    public void testRelatedUsingSpecterRankProfile() {
        Query query = new Query("?query=related_to:123&type=any&use-specter&ranking=bm25");
        Result result = execute(query, new RelatedArticlesByNNSearcher(), new MockBackend());
        assertEquals("+" + specterNNItem + " -id:123",
                result.getQuery().getModel().getQueryTree().toString());
        assertEquals(query.getRanking().getProfile(), "related-specter");
    }


    private Result execute(Query query, Searcher... searcher) {
        Execution execution = new Execution(new Chain<>(searcher), Execution.Context.createContextStub());
        return execution.search(query);
    }

    /** Handles queries fetching a specific article */
    private static class MockBackend extends Searcher {

        @Override
        public Result search(Query query, Execution execution) {
            if (isArticleRequest(query)) {
                Result result = execution.search(query);
                result.setTotalHitCount(1);
                Hit articleHit = new Hit("ignored", 1.0);
                articleHit.setField("title_embedding", mockEmbedding());
                articleHit.setField("abstract_embedding", mockEmbedding());
                articleHit.setField("specter_embedding", mockEmbedding());
                result.hits().add(articleHit);
                return result;
            }
            else {
                return execution.search(query);
            }
        }

        private Tensor mockEmbedding() {
            Tensor.Builder b = Tensor.Builder.of(TensorType.fromSpec("tensor<float>(x[768])"));
            for (long i = 0; i < 768; i++)
                b.cell(Math.random(), i);
            return b.build();
        }

        private boolean isArticleRequest(Query query) {
            if (query.getHits() != 1) return false;
            if ( ! (query.getModel().getQueryTree().getRoot() instanceof WordItem)) return false;
            WordItem word = (WordItem)query.getModel().getQueryTree().getRoot();
            if ( ! "id".equals(word.getIndexName())) return false;
            return true;
        }

    }

}
