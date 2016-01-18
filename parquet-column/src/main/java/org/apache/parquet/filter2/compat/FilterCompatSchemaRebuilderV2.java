/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.parquet.filter2.compat;

import org.apache.parquet.ShouldNeverHappenException;
import org.apache.parquet.hadoop.metadata.ColumnPath;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class FilterCompatSchemaRebuilderV2 {

  public static MessageType rebuildSchema(FilterCompat.FilterPredicateCompat filterPredicateCompat) {
    Set<ColumnPath> columnPaths = FilterCompatColumnCollector.INSTANCE.visit(filterPredicateCompat);
    NameAndChildren root = buildNameAndChildren(columnPaths);
    MessageType messageType = null;
    if (root != null) {
      messageType = root.asMessageType();
    }
    return messageType;
  }

  private static NameAndChildren buildNameAndChildren(Set<ColumnPath> columnPaths) {
    NameAndChildren root = null;
    if (columnPaths != null) {
      root = new NameAndChildren("root", NameAndChildren.TYPE.GROUP);
      Iterator<ColumnPath> columnPathIt = columnPaths.iterator();
      while (columnPathIt.hasNext()) {
        ColumnPath columnPath = columnPathIt.next();
        String[] p = columnPath.toArray();
        if (p != null) {
          NameAndChildren group = root;
          for (int i = 0; i < p.length - 1; i++) {
            String groupName = p[i];
            if (!group.containsField(groupName)) {
              group.add(new NameAndChildren(groupName, NameAndChildren.TYPE.GROUP));
            }
          }
          String primitiveName = p[p.length - 1];
          if (!group.containsField(primitiveName)) {
            group.add(new NameAndChildren(primitiveName, NameAndChildren.TYPE.PRIMITIVE));
          }
        }
      }
    }
    return root;
  }

  private static class NameAndChildren {

    public enum TYPE {
      GROUP, PRIMITIVE
    }

    public final String name;
    public final TYPE type;
    public List<NameAndChildren> children = null;

    public NameAndChildren(String name, TYPE type) {
      this.name = name;
      this.type = type;
    }

    public void add(NameAndChildren child) {
      if (children == null) {
        children = new LinkedList<NameAndChildren>();
      }
      children.add(child);
    }

    public boolean containsField(String field) {
      return children != null && children.contains(field);
    }


    public MessageType asMessageType() {
      return new MessageType("root", childrenAsTypes());
    }

    private List<Type> childrenAsTypes() {
      List<Type> types = new LinkedList<Type>();
      if (children == null || children.size() == 0) {
        throw new ShouldNeverHappenException();
      }
      for (NameAndChildren child : children) {
        types.add(child.asType());
      }
      return types;
    }

    private Type asType() {
      Type t;
      switch (type) {
        case GROUP:
          t = new GroupType(Type.Repetition.OPTIONAL, name, childrenAsTypes());
          break;
        case PRIMITIVE:
          t = new PrimitiveType(Type.Repetition.OPTIONAL, PrimitiveType.PrimitiveTypeName.INT32, name);
          break;
        default:
          throw new ShouldNeverHappenException();
      }
      return t;
    }
  }
}