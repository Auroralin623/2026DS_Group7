package edu.nccu.moviefinder.model;

import java.util.ArrayList;

/** 網站樹的一個節點,一個節點包一個網頁。 */
public class WebNode {
    /** 子節點分數的折扣係數(proposal §2.4)。 */
    public static final double CHILD_DISCOUNT = 0.5;

    private final WebPage webPage;
    private WebNode parent;
    private final ArrayList<WebNode> children = new ArrayList<>();
    private double nodeScore;

    public WebNode(WebPage webPage) {
        this.webPage = webPage;
    }

    /** 假資料用:直接指定節點分數。 */
    public static WebNode fixture(WebPage webPage, double nodeScore) {
        WebNode node = new WebNode(webPage);
        node.nodeScore = nodeScore;
        return node;
    }

    public void addChild(WebNode child) {
        child.parent = this;
        children.add(child);
    }

    /**
     * nodeScore = webPage.score + CHILD_DISCOUNT × Σ child.nodeScore。
     * 呼叫前,自己的 WebPage 分數和所有子節點的 nodeScore 都必須已算好。
     */
    public void setNodeScore() {
        double childScores = 0.0;
        for (WebNode child : children) {
            childScores += child.nodeScore;
        }
        nodeScore = webPage.getScore() + CHILD_DISCOUNT * childScores;
    }

    public boolean isRoot() {
        return parent == null;
    }

    /** 根節點深度為 0,印樹時用來決定縮排。 */
    public int getDepth() {
        int depth = 0;
        WebNode ancestor = parent;
        while (ancestor != null) {
            depth++;
            ancestor = ancestor.parent;
        }
        return depth;
    }

    public WebPage getWebPage() {
        return webPage;
    }

    public WebNode getParent() {
        return parent;
    }

    public ArrayList<WebNode> getChildren() {
        return children;
    }

    public double getNodeScore() {
        return nodeScore;
    }
}
