/*
 * Copyright (c) 2010- Austrian Research Institute for Artificial Intelligence (OFAI). 
 * Copyright (C) 2014-2016 The University of Sheffield.
 *
 * This file is part of gateplugin-ModularPipelines
 * (see https://github.com/johann-petrak/gateplugin-ModularPipelines)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software. If not, see <http://www.gnu.org/licenses/>.
 */
package com.jpetrak.gate.stringannotation.extendedgazetteer.trie;


public abstract class Trie3State extends com.jpetrak.gate.stringannotation.extendedgazetteer.State {
  
  public abstract void put(char key, Trie3State value);
  
  protected int lookupIndex = -1;
  public int getLookupIndex() {
    return lookupIndex;
  }

  public void addLookup(int index) {
    lookupIndex = index;
  }
  
  @Override
  public boolean isFinal() {
    return lookupIndex >= 0;
  }    
  
  @Override
  public abstract Trie3State next(char chr);
  
  public abstract void replace(char key, Trie3State newState, Trie3State oldState);
}
