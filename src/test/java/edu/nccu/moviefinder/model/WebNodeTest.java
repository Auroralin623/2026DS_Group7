package edu.nccu.moviefinder.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import org.junit.jupiter.api.Test;

class WebNodeTest {

    @Test
    void nodeScoreAddsHalfOfTheChildrenScoreSumToItsOwnPageScore() {
        WebNode parent = new WebNode(WebPage.fixture("parent", "parent", new HashMap<>(), 10));
        parent.addChild(WebNode.fixture(WebPage.fixture("child-1", "child-1", new HashMap<>(), 4), 4));
        parent.addChild(WebNode.fixture(WebPage.fixture("child-2", "child-2", new HashMap<>(), 2), 2));

        parent.setNodeScore();

        assertEquals(13.0, parent.getNodeScore());
    }

    @Test
    void nodeWithoutChildrenGetsItsOwnPageScore() {
        WebNode node = new WebNode(WebPage.fixture("page", "page", new HashMap<>(), 10));

        node.setNodeScore();

        assertEquals(10.0, node.getNodeScore());
    }

    @Test
    void depthCountsEdgesFromRoot() {
        WebNode root = new WebNode(WebPage.fixture("root", "root", new HashMap<>(), 0));
        WebNode child = new WebNode(WebPage.fixture("child", "child", new HashMap<>(), 0));
        WebNode grandchild = new WebNode(WebPage.fixture("grandchild", "grandchild", new HashMap<>(), 0));
        root.addChild(child);
        child.addChild(grandchild);

        assertEquals(0, root.getDepth());
        assertEquals(1, child.getDepth());
        assertEquals(2, grandchild.getDepth());
    }
}
