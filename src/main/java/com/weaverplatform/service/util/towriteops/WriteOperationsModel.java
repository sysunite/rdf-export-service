package com.weaverplatform.service.util.towriteops;

import com.weaverplatform.protocol.model.*;
import com.weaverplatform.service.util.Cuid;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.RDFParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * @author bastbijl, Sysunite 2017
 */
public class WriteOperationsModel extends RdfModel {

  private static final Logger log = LoggerFactory.getLogger(WriteOperationsModel.class);

  private String user;

  final HashSet<String> doneNodeItems = new HashSet<>();
  final LinkedList<Item> nodeItems = new LinkedList<>();
  final LinkedList<AttributeItem> attributeItems = new LinkedList<>();
  final LinkedList<RelationItem> relationItems = new LinkedList<>();

  public WriteOperationsModel(String user) {
    this.user = user;
  }

  public void setGraph(String graph) {
    this.defaultGraph = graph;
  }

  public void setGraphMap(HashMap<String, String> map) {
    this.graphMap = map;
  }

  public void setDismissGraphs(HashSet<String> dismissGraphs) {
    this.dismissGraphs = dismissGraphs;
  }



  @Override
  public boolean addResourceStatement(Resource subject, Resource predicate, Resource object, Resource context) {
    if(RDF.TYPE.equals(predicate) && RdfModel.RESTRICTION_IRIS.contains(object)) {
      restrictionIds.add(getItem(getId(subject)));
    }

    if(!filterRestriction(getItem(getId(subject))) || !filterRestriction(getItem(getId(predicate))) || !filterRestriction(getItem(getId(object)))) {
      return false;
    }

    RelationItem item = new RelationItem();
    item.sourceId = getItem(getId(subject));
    item.key = getItem(getId(predicate));
    item.toId = getItem(getId(object));

    relationItems.add(item);
    return true;
  }

  @Override
  public boolean addLiteralStatement(Resource subject, Resource predicate, Literal object, Resource context) {


    if(!filterRestriction(getItem(getId(subject))) || !filterRestriction(getItem(getId(predicate)))) {
      return false;
    }


    if(onlyPreferredLanguage) {
      AttributeItem item = getItem(getId(subject)).getAttribute(getItem(getId(predicate)), attributeItems);
      item.sourceId = getItem(getId(subject));
      item.key = getItem(getId(predicate));

      if (preferOver(object, item.literal)) {
        item.literal = object;
      }
    } else {
      AttributeItem item = new AttributeItem();
      item.sourceId = getItem(getId(subject));
      item.key = getItem(getId(predicate));
      item.literal = object;
    }

    return true;
  }







  public boolean hasNext() {
    return size() > 0;
  }

  public void readStream(InputStream stream, String emptyPrefix, RDFParser rdfParser) {
    super.readStream(stream, emptyPrefix, rdfParser);

    for(String finalId : items.keySet()) {
      if(!doneNodeItems.contains(finalId)) {
        nodeItems.add(items.get(finalId));
      }
    }
    doneNodeItems.addAll(items.keySet());
  }

  @Override
  public int size() {
    return  nodeItems.size() + attributeItems.size() + relationItems.size();
  }

  @Override
  public boolean isEmpty() {
    return !hasNext();
  }

  private boolean storeThisGraph(String graphId) {
   return (dismissGraphs == null || !dismissGraphs.contains(graphId));
  }

  public ArrayList<SuperOperation> next(int size) {

    ArrayList<SuperOperation> list = new ArrayList<>();

    while(list.size() < size && !nodeItems.isEmpty()) {
      Item nodeItem = nodeItems.remove(0);
      if(filterRestriction(nodeItem) && storeThisGraph(nodeItem.graphId)) {
        SuperOperation operation = new SuperOperation();
        operation.setAction("create-node");
        operation.setId(nodeItem.finalId);
        operation.setUser(user);
        operation.setGraph(nodeItem.graphId);
        list.add(operation);
      }
    }

    while(list.size() < size && !attributeItems.isEmpty()) {
      AttributeItem item = attributeItems.remove(0);
      if(filterRestriction(item.sourceId)) {
        String graph = defaultGraph;
        if(storeThisGraph(item.sourceId.graphId)) {
          graph = item.sourceId.graphId;
        }
        Object value = getValue(item.literal);
        AttributeDataType dataType = getDataType(item.literal);
        SuperOperation operation = new SuperOperation();
        operation.setAction("create-attribute");
        operation.setId(Cuid.createCuid());
        operation.setGraph(graph);
        operation.setUser(user);
        operation.setSourceId(item.sourceId.finalId);
        operation.setSourceGraph(item.sourceId.graphId);
        operation.setKey(item.key.finalId);
        operation.setValue(value);
        operation.setDatatype(dataType);
        list.add(operation);
      }
    }

    while(list.size() < size && !relationItems.isEmpty()) {
      RelationItem item = relationItems.remove(0);
      if(filterRestriction(item.sourceId) && filterRestriction(item.toId)) {
        String graph = defaultGraph;
        if(storeThisGraph(item.sourceId.graphId)) {
          graph = item.sourceId.graphId;
        }
        SuperOperation operation = new SuperOperation();
        operation.setAction("create-relation");
        operation.setUser(user);
        operation.setId(Cuid.createCuid());
        operation.setGraph(graph);
        operation.setSourceId(item.sourceId.finalId);
        operation.setSourceGraph(item.sourceId.graphId);
        operation.setKey(item.key.finalId);
        operation.setTargetId(item.toId.finalId);
        operation.setTargetGraph(item.toId.graphId);
        list.add(operation);
      }
    }

    return list;
  }
}
