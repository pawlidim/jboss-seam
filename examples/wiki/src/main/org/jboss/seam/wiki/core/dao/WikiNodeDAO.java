package org.jboss.seam.wiki.core.dao;

import org.hibernate.Session;
import org.hibernate.transform.ResultTransformer;
import org.jboss.seam.Component;
import org.jboss.seam.annotations.AutoCreate;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Logger;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.log.Log;
import org.jboss.seam.wiki.core.model.*;
import org.jboss.seam.wiki.core.nestedset.NestedSetNode;
import org.jboss.seam.wiki.core.nestedset.query.NestedSetDuplicator;
import org.jboss.seam.wiki.core.nestedset.query.NestedSetNodeWrapper;
import org.jboss.seam.wiki.core.nestedset.query.NestedSetQueryBuilder;
import org.jboss.seam.wiki.core.nestedset.query.NestedSetResultTransformer;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.NoResultException;
import java.util.*;

/**
 * DAO for nodes, transparently respects security access levels.
 * <p>
 * All node access should go through this component, this component knows
 * about access levels because it relies on a restricted (filtered) Entitymanager.
 *
 * @author Christian Bauer
 *
 */
@Name("wikiNodeDAO")
@AutoCreate
public class WikiNodeDAO {

    @Logger
    static Log log;

    // Most of the DAO methods use this
    @In protected EntityManager restrictedEntityManager;

    // Some run unrestricted (e.g. internal unique key validation of wiki names)
    // Make sure that these methods do not return detached objects!
    @In
    protected EntityManager entityManager;

    public void makePersistent(WikiNode node) {
        entityManager.persist(node);
    }

    public WikiNode findWikiNode(Long nodeId) {
        try {
            return (WikiNode) restrictedEntityManager
                    .createQuery("select n from WikiNode n where n.id = :id")
                    .setParameter("id", nodeId)
                    .setHint("org.hibernate.comment", "Find wikinode by id")
                    .getSingleResult();
        } catch (EntityNotFoundException ex) {
        } catch (NoResultException ex) {
        }
        return null;

    }

    public List<WikiNode> findWikiNodes(List<Long> ids) {
        return restrictedEntityManager
                .createQuery("select n from WikiNode n where n.id in (:idList)")
                .setParameter("idList", ids)
                .setHint("org.hibernate.comment", "Find wikinodes by id list")
                .getResultList();
    }

    public WikiNode findWikiNodeInArea(Long areaNumber, String wikiname) {
        return findWikiNodeInArea(areaNumber, wikiname, restrictedEntityManager);
    }

    public WikiNode findWikiNodeInArea(Long areaNumber, String wikiname, EntityManager em) {
        try {
            return (WikiNode) em
                    .createQuery("select n from WikiNode n where n.areaNumber = :areaNumber and n.wikiname = :wikiname")
                    .setParameter("areaNumber", areaNumber)
                    .setParameter("wikiname", wikiname)
                    .setHint("org.hibernate.comment", "Find node in area")
                    .getSingleResult();
        } catch (EntityNotFoundException ex) {
        } catch (NoResultException ex) {
        }
        return null;
    }

    public Long findChildrenCount(WikiNode node) {
        try {
            return (Long) restrictedEntityManager
                    .createQuery("select count(n) from WikiNode n where n.parent = :parent")
                    .setParameter("parent", node)
                    .setHint("org.hibernate.comment", "Find number of wikinode children")
                    .setHint("org.hibernate.cacheable", true)
                    .getSingleResult();
        } catch (EntityNotFoundException ex) {
        } catch (NoResultException ex) {
        }
        return null;
    }

    public List<WikiNode> findChildren(WikiNode node, String orderByProperty, boolean orderAscending, int firstResult, int maxResults) {
        // Sanitize input
        orderByProperty = orderByProperty.replaceAll("[^a-zA-Z0-9]", "");

        StringBuilder queryString = new StringBuilder();
        queryString.append("select n from WikiNode n where n.parent = :parent").append(" ");
        queryString.append("order by n.").append(orderByProperty).append(" ").append(orderAscending ? "asc" : "desc");

        return restrictedEntityManager
                .createQuery(queryString.toString())
                .setHint("org.hibernate.comment", "Find wikinode children order by "+orderByProperty)
                .setParameter("parent", node)
                .setFirstResult(firstResult)
                .setMaxResults(maxResults)
                .getResultList();
    }

    public List<WikiDirectory> findChildWikiDirectories(WikiDirectory dir) {
        return restrictedEntityManager
                .createQuery("select d from WikiDirectory d left join fetch d.feed where d.parent = :parent")
                .setHint("org.hibernate.comment", "Find wikinode children directories")
                .setParameter("parent", dir)
                .getResultList();
    }

    public WikiComment findWikiComment(Long commentId) {
        try {
            return (WikiComment) restrictedEntityManager
                    .createQuery("select c from WikiComment c where c.id = :id")
                    .setParameter("id", commentId)
                    .setHint("org.hibernate.comment", "Find comment by id")
                    .getSingleResult();
        } catch (EntityNotFoundException ex) {
        } catch (NoResultException ex) {
        }
        return null;
    }

    public List<WikiComment> findWikiCommentsThreaded(WikiDocument document) {
        return findWikiComments(document, true, false);
    }

    public List<WikiComment> findWikiCommentsFlat(WikiDocument document, boolean orderbyDateAscending) {
        return findWikiComments(document, false, orderbyDateAscending);
    }

    private List<WikiComment> findWikiComments(WikiDocument document, final boolean threaded, boolean unthreadedAscending) {
        StringBuilder queryString = new StringBuilder();

        NestedSetQueryBuilder queryBuilder = new NestedSetQueryBuilder(new WikiComment(), false, true);
        queryString.append("select ").append(queryBuilder.getSelectLevelClause()).append(", ");
        queryString.append(queryBuilder.getSelectNodeClause()).append(" ");
        queryString.append("from ").append(queryBuilder.getFromClause()).append(" ");
        queryString.append("where ").append(queryBuilder.getWhereClause(false)).append(" ");
        queryString.append("and ").append(NestedSetQueryBuilder.NODE2_ALIAS).append(".nodeInfo.nsThread in ");
        queryString.append("(select c3.nodeInfo.nsThread from WikiComment c3 where c3.parent = :doc)").append(" ");
        queryString.append("group by ").append(queryBuilder.getGroupByClause()).append(" ");
        queryString.append("order by ");
        if (threaded) {
            queryString.append(NestedSetQueryBuilder.NODE_ALIAS).append(".nodeInfo.nsThread asc").append(", ");
            queryString.append(queryBuilder.getOrderByClause());
        } else {
            queryString.append(NestedSetQueryBuilder.NODE_ALIAS).append(".createdOn ").append(unthreadedAscending ? "asc" : "desc");
        }

        org.hibernate.Query nsQuery = getSession(true).createQuery(queryString.toString());
        nsQuery.setParameter("doc", document);
        nsQuery.setComment("Find wikicomments (tree)");
        nsQuery.setResultTransformer(
            new ResultTransformer() {
                public Object transformTuple(Object[] objects, String[] aliases) {
                    Long level = (Long)objects[0];
                    WikiComment c = (WikiComment)objects[1];
                    if (threaded) c.setLevel(level);
                    return c;
                }
                public List transformList(List list) {
                    return list;
                }
            }
        );
        return nsQuery.list();
    }

    public WikiFile findWikiFile(Long fileId) {
        try {
            return (WikiFile) restrictedEntityManager
                    .createQuery("select f from WikiFile f where f.id = :id")
                    .setParameter("id", fileId)
                    .setHint("org.hibernate.comment", "Find wikifile by id")
                    .getSingleResult();
        } catch (EntityNotFoundException ex) {
        } catch (NoResultException ex) {
        }
        return null;
    }

    public WikiFile findWikiFileInArea(Long areaNumber, String wikiname) {
        return findWikiFileInArea(areaNumber, wikiname, restrictedEntityManager);
    }

    public WikiFile findWikiFileInArea(Long areaNumber, String wikiname, EntityManager em) {
        try {
            return (WikiFile) em
                    .createQuery("select f from WikiFile f where f.areaNumber = :areaNumber and f.wikiname = :wikiname")
                    .setParameter("areaNumber", areaNumber)
                    .setParameter("wikiname", wikiname)
                    .setHint("org.hibernate.comment", "Find wikifile in area")
                    .getSingleResult();
        } catch (EntityNotFoundException ex) {
        } catch (NoResultException ex) {
        }
        return null;
    }

    public WikiDocument findWikiDocument(Long documentId) {
        try {
            return (WikiDocument) restrictedEntityManager
                    .createQuery("select d from WikiDocument d where d.id = :id")
                    .setParameter("id", documentId)
                    .setHint("org.hibernate.comment", "Find document by id")
                    .getSingleResult();
        } catch (EntityNotFoundException ex) {
        } catch (NoResultException ex) {
        }
        return null;
    }

    // Access restricted version of directory.getDefaultFile()
    public WikiFile findDefaultWikiFile(WikiDirectory directory) {
        if (directory == null) return null;
        try {
            return (WikiFile) restrictedEntityManager
                    .createQuery("select d.defaultFile from WikiDirectory d where d = :dir")
                    .setParameter("dir", directory)
                    .setHint("org.hibernate.comment", "Find default file")
                    .getSingleResult();
        } catch (EntityNotFoundException ex) {
        } catch (NoResultException ex) {
        }
        return null;
    }
    // Access restricted version of directory.getDefaultFile(), also narrows it down to a document (see WikiRequestResolver)
    public WikiDocument findDefaultDocument(WikiDirectory directory) {
        if (directory == null) return null;
        try {
            return (WikiDocument) restrictedEntityManager
                    .createQuery("select doc from WikiDocument doc, WikiDirectory d where d = :dir and doc.id = d.defaultFile.id")
                    .setParameter("dir", directory)
                    .setHint("org.hibernate.comment", "Find default doc")
                    .getSingleResult();
        } catch (EntityNotFoundException ex) {
        } catch (NoResultException ex) {
        }
        return null;
    }

    public List<WikiDocument> findWikiDocuments(WikiDirectory directory) {
        return restrictedEntityManager.createQuery("select d from WikiDocument d where d.parent = :dir order by d.createdOn asc")
                .setParameter("dir", directory)
                .setHint("org.hibernate.comment", "Find documents of directory")
                .getResultList();
    }

    public WikiDocument findWikiDocumentInArea(Long areaNumber, String wikiname) {
        return findWikiDocumentInArea(areaNumber, wikiname, restrictedEntityManager);
    }

    public WikiDocument findWikiDocumentInArea(Long areaNumber, String wikiname, EntityManager em) {
        try {
            return (WikiDocument) em
                    .createQuery("select d from WikiDocument d where d.areaNumber = :areaNumber and d.wikiname = :wikiname")
                    .setParameter("areaNumber", areaNumber)
                    .setParameter("wikiname", wikiname)
                    .setHint("org.hibernate.comment", "Find document in area")
                    .getSingleResult();
        } catch (EntityNotFoundException ex) {
        } catch (NoResultException ex) {
        }
        return null;
    }
    
    public List<WikiDocument> findWikiDocumentsOrderByLastModified(int maxResults) {
        //noinspection unchecked
        return (List<WikiDocument>)restrictedEntityManager
                .createQuery("select d from WikiDocument d where d.lastModifiedOn is not null order by d.lastModifiedOn desc")
                .setHint("org.hibernate.comment", "Find documents order by lastModified")
                .setMaxResults(maxResults)
                .getResultList();
    }

    public WikiDocument findSiblingWikiDocumentInDirectory(WikiDocument currentDocument, String byProperty, boolean previousOrNext) {
        byProperty = byProperty.replaceAll("[^\\p{Alnum}]+", ""); // Avoid SQL injection hole!
        try {
            return (WikiDocument)restrictedEntityManager
                    .createQuery("select sibling from WikiDocument sibling, WikiDocument current" +
                                 " where sibling.parent = current.parent and current = :current and not sibling = :current" +
                                 " and sibling."+ byProperty + " " + (previousOrNext ? "<=" : ">=") + "current."+byProperty +
                                 " order by sibling." +byProperty + " " + (previousOrNext ? "desc" : "asc") )
                    .setMaxResults(1)
                    .setParameter("current", currentDocument)
                    .getSingleResult();
            } catch (EntityNotFoundException ex) {
            } catch (NoResultException ex) {
        }
        return null;
    }

    public WikiUpload findWikiUpload(Long uploadId) {
        try {
            return (WikiUpload) restrictedEntityManager
                    .createQuery("select u from WikiUpload u where u.id = :id")
                    .setParameter("id", uploadId)
                    .setHint("org.hibernate.comment", "Find upload by id")
                    .getSingleResult();
        } catch (EntityNotFoundException ex) {
        } catch (NoResultException ex) {
        }
        return null;
    }

    public List<WikiUpload> findWikiUploads(WikiDirectory directory) {
        return restrictedEntityManager.createQuery("select u from WikiUpload u where u.parent = :dir order by u.createdOn asc")
                .setParameter("dir", directory)
                .setHint("org.hibernate.comment", "Find uploads of directory")
                .getResultList();
    }

    public WikiDirectory findWikiDirectory(Long directoryId) {
        try {
            return (WikiDirectory) restrictedEntityManager
                    .createQuery("select d from WikiDirectory d left join fetch d.feed where d.id = :id")
                    .setParameter("id", directoryId)
                    .setHint("org.hibernate.comment", "Find directory by id")
                    .getSingleResult();
        } catch (EntityNotFoundException ex) {
        } catch (NoResultException ex) {
        }
        return null;
    }

    public WikiDirectory findWikiDirectoryInArea(Long areaNumber, String wikiname) {
        return findWikiDirectoryInArea(areaNumber, wikiname, restrictedEntityManager);
    }

    public WikiDirectory findWikiDirectoryInArea(Long areaNumber, String wikiname, EntityManager em) {
        try {
            return (WikiDirectory) em
                    .createQuery("select d from WikiDirectory d left join fetch d.feed where d.areaNumber = :areaNumber and d.wikiname = :wikiname")
                    .setParameter("areaNumber", areaNumber)
                    .setParameter("wikiname", wikiname)
                    .setHint("org.hibernate.comment", "Find directory in area")
                    .getSingleResult();
        } catch (EntityNotFoundException ex) {
        } catch (NoResultException ex) {
        }
        return null;
    }

    public WikiDirectory findArea(String wikiname) {
        try {
            return (WikiDirectory) restrictedEntityManager
                    .createQuery("select d from WikiDirectory d left join fetch d.feed, WikiDirectory r where r.parent is null and d.parent = r and d.wikiname = :wikiname")
                    .setParameter("wikiname", wikiname)
                    .setHint("org.hibernate.comment", "Find area by wikiname")
                    .getSingleResult();
        } catch (EntityNotFoundException ex) {
        } catch (NoResultException ex) {
        }
        return null;
    }

    public WikiDirectory findArea(Long areaNumber) {
        try {
            return (WikiDirectory) restrictedEntityManager
                    .createQuery("select d from WikiDirectory d left join fetch d.feed, WikiDirectory r where r.parent is null and d.parent = r and d.areaNumber = :areaNumber")
                    .setParameter("areaNumber", areaNumber)
                    .setHint("org.hibernate.comment", "Find area by area number")
                    .getSingleResult();
        } catch (EntityNotFoundException ex) {
        } catch (NoResultException ex) {
        }
        return null;
    }

    // Returns a detached object
    public WikiFile findHistoricalFile(String entityName, Long historyId) {
        WikiFile historicalFile = (WikiFile)getSession(true).get(entityName, historyId);
        getSession(true).evict(historicalFile);
        return historicalFile;
    }

    public List<WikiFile> findHistoricalFiles(WikiFile file) {
        if (file == null || file.getId() == null) return null;
        return getSession(true).createQuery("select f from " + file.getHistoricalEntityName() + " f where f.id = :fileId order by f.revision desc")
                                .setParameter("fileId", file.getId())
                                .list();
    }

    public Long findNumberOfHistoricalFiles(WikiFile file) {
        if (file == null || file.getId() == null) return 0l;
        return (Long)getSession(true).createQuery("select count(f) from " + file.getHistoricalEntityName() + " f where f.id = :fileId")
                                  .setParameter("fileId", file.getId())
                                  .setCacheable(true)
                                  .uniqueResult();
    }

    public void persistHistoricalFile(WikiFile historicalFile) {
        if (historicalFile.getHistoricalEntityName() != null) {
            log.debug("persisting historical file: " + historicalFile + " as revision: " + historicalFile.getRevision());
            // Use the nonrestricted persistence context, which should be safe here so we can flush it again
            // TODO: I wish Hibernate/JBoss weren't so retarted about getting a StatelessSession when you need one...
            getSession(false).persist(historicalFile.getHistoricalEntityName(), historicalFile);
            getSession(false).flush();
            getSession(false).evict(historicalFile);
        }
    }

    public void removeHistoricalFiles(WikiFile file) {
        if (file == null || file.getId() == null) return;
        ((WikiFile)getSession(true).load(WikiFile.class, file.getId())).setRevision(0);
        getSession(true).flush();
        getSession(true).createQuery("delete from " + file.getHistoricalEntityName() + " f where f.id = :fileId")
                         .setParameter("fileId", file.getId())
                         .executeUpdate();
    }

    // Multi-row constraint validation
    public boolean isUniqueWikiname(Long areaNumber, WikiNode node) {
        WikiNode foundNode = findWikiNodeInArea(areaNumber, node.getWikiname(), entityManager);
        return foundNode == null || node.getId() != null && node.getId().equals(foundNode.getId());
    }

    public boolean isUniqueWikiname(Long areaNumber, String wikiname) {
        WikiNode foundNode = findWikiNodeInArea(areaNumber, wikiname, entityManager);
        return foundNode == null;
    }

    public List<WikiMenuItem> findMenuItems(WikiDirectory parentDir) {
        return restrictedEntityManager.createQuery("select m from WikiMenuItem m where m.directory.parent = :parent order by m.displayPosition asc")
                .setParameter("parent", parentDir)
                .getResultList();
    }

    public NestedSetNodeWrapper<WikiDirectory> findMenuItemTree(WikiDirectory startDir, Long maxDepth, Long flattenToLevel, boolean showAdminOnly) {

        NestedSetNodeWrapper<WikiDirectory> startNodeWrapper = new NestedSetNodeWrapper<WikiDirectory>(startDir, getComparatorDisplayPosition());
        NestedSetResultTransformer<WikiDirectory> transformer =
                new NestedSetResultTransformer<WikiDirectory>(startNodeWrapper, flattenToLevel);
        transformer.getAdditionalProjections().put("displayPosition", "m.displayPosition");

        // Make hollow copies for menu display so that changes to the model in the persistence context don't appear
        transformer.setNestedSetDuplicator(
            new NestedSetDuplicator<WikiDirectory>() {
                public WikiDirectory duplicate(WikiDirectory original) {
                    WikiDirectory copy = new WikiDirectory();
                    copy.flatCopy(original, false);
                    copy.setId(original.getId());
                    copy.setParent(original.getParent());
                    return copy;
                }
            }
        );

        appendNestedSetNodes(transformer, maxDepth, showAdminOnly, "WikiMenuItem m", "m.id = n1.id");
        return startNodeWrapper;

    }

    public NestedSetNodeWrapper<WikiDirectory> findWikiDirectoryTree(WikiDirectory startDir, Long maxDepth, Long flattenToLevel, boolean showAdminOnly) {

        NestedSetNodeWrapper<WikiDirectory> startNodeWrapper = new NestedSetNodeWrapper<WikiDirectory>(startDir, getComparatorWikiDirectoryName());
        NestedSetResultTransformer<WikiDirectory> transformer = new NestedSetResultTransformer<WikiDirectory>(startNodeWrapper, flattenToLevel);
        appendNestedSetNodes(transformer, maxDepth, showAdminOnly, null);
        return startNodeWrapper;

    }

    public <N extends NestedSetNode> void appendNestedSetNodes(NestedSetResultTransformer<N> transformer,
                                                                   Long maxDepth,
                                                                   boolean showAdminOnly,
                                                                   String selectionFragment,
                                                                   String... restrictionFragment) {

        N startNode = transformer.getRootWrapper().getWrappedNode();

        log.debug("appending nested set nodes to node: " + startNode + ", " + startNode.getNodeInfo());

        NestedSetQueryBuilder builder = new NestedSetQueryBuilder(startNode, false);

        StringBuilder queryString = new StringBuilder();

        queryString.append("select").append(" ");
        queryString.append(builder.getSelectLevelClause()).append(", ");
        queryString.append(builder.getSelectNodeClause()).append(" ");
        for (Map.Entry<String, String> entry : transformer.getAdditionalProjections().entrySet()) {
            queryString.append(", ").append(entry.getValue()).append(" as ").append(entry.getKey()).append(" ");
        }
        queryString.append("from ").append(builder.getFromClause());
        if (selectionFragment != null) {
            queryString.append(", ").append(selectionFragment);
        }
        queryString.append(" where ").append(builder.getWhereClause()).append(" ");
        if (showAdminOnly) {
            queryString.append("and ").append(NestedSetQueryBuilder.NODE_ALIAS).append(".createdBy = :adminUser").append(" ");
        }
        for (String fragment: restrictionFragment) {
            queryString.append("and ").append(fragment).append(" ");
        }
        queryString.append("group by ").append(builder.getGroupByClause()).append(" ");
        for (Map.Entry<String, String> entry : transformer.getAdditionalProjections().entrySet()) {
            queryString.append(", ").append(entry.getValue()).append(" ");
        }

        if (maxDepth != null) {
            queryString.append("having count(").append(NestedSetQueryBuilder.NODE_ALIAS).append(".id) <= :maxDepth").append(" ");
        }

        queryString.append("order by ").append(builder.getOrderByClause());

        org.hibernate.Query nestedSetQuery = getSession(true).createQuery(queryString.toString());
        nestedSetQuery.setParameter("nsThread", startNode.getNodeInfo().getNsThread());
        nestedSetQuery.setParameter("nsLeft", startNode.getNodeInfo().getNsLeft());
        nestedSetQuery.setParameter("nsRight", startNode.getNodeInfo().getNsRight());
        if (showAdminOnly) nestedSetQuery.setParameter("adminUser", Component.getInstance("adminUser"));
        if (maxDepth != null) nestedSetQuery.setParameter("maxDepth", maxDepth);

        nestedSetQuery.setComment("Appending nested set nodes to startnode: " + startNode.getId());

        nestedSetQuery.setResultTransformer(transformer);
        nestedSetQuery.list(); // Append all children hierarchically to the transformers rootWrapper
    }

    public Comparator<NestedSetNodeWrapper<WikiDirectory>> getComparatorWikiDirectoryName() {
        // Needs to be equals() safe (SortedSet):
        // - compare by name, if equal
        // - compare by id
        return
            new Comparator<NestedSetNodeWrapper<WikiDirectory>>() {
                public int compare(NestedSetNodeWrapper<WikiDirectory> o1, NestedSetNodeWrapper<WikiDirectory> o2) {
                    WikiDirectory node1 = o1.getWrappedNode();
                    WikiDirectory node2 = o2.getWrappedNode();
                    if (node1.getName().compareTo(node2.getName()) != 0) {
                        return node1.getName().compareTo(node2.getName());
                    }
                    return node1.getId().compareTo(node2.getId());
                }
            };
    }

    public Comparator<NestedSetNodeWrapper<WikiDirectory>> getComparatorDisplayPosition() {
        // Needs to be equals() safe (SortedSet):
        // - compare by display position, if equal
        // - compare by name, if equal
        // - compare by id
        return
            new Comparator<NestedSetNodeWrapper<WikiDirectory>>() {
                public int compare(NestedSetNodeWrapper<WikiDirectory> o1, NestedSetNodeWrapper<WikiDirectory> o2) {
                    WikiDirectory node1 = o1.getWrappedNode();
                    Long node1DisplayPosition = (Long)o1.getAdditionalProjections().get("displayPosition");
                    WikiDirectory node2 = o2.getWrappedNode();
                    Long node2DisplayPosition = (Long)o2.getAdditionalProjections().get("displayPosition");
                    if (node1DisplayPosition.compareTo(node2DisplayPosition) != 0) {
                        return node1DisplayPosition.compareTo(node2DisplayPosition);
                    } else if (node1.getName().compareTo(node2.getName()) != 0) {
                        return node1.getName().compareTo(node2.getName());
                    }
                    return node1.getId().compareTo(node2.getId());
                }
            };
    }

    private Session getSession(boolean restricted) {
        if (restricted) {
            return ((Session)((org.jboss.seam.persistence.EntityManagerProxy) restrictedEntityManager).getDelegate());
        } else {
            return ((Session)((org.jboss.seam.persistence.EntityManagerProxy) entityManager).getDelegate());
        }
    }

}
