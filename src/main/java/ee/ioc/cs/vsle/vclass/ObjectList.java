package ee.ioc.cs.vsle.vclass;

/*-
 * #%L
 * CoCoViLa
 * %%
 * Copyright (C) 2003 - 2017 Institute of Cybernetics at Tallinn University of Technology
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import ee.ioc.cs.vsle.util.*;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A <tt>List</tt> for storing and organizing scheme objects.
 */
public class ObjectList extends ArrayList<GObj> {

	private static final long serialVersionUID = 1L;

	public ObjectList() {
		super();
	}

	/**
	 * Constructs an <tt>ObjectList</tt> containing the elements
	 * of the specified collection, in the order they are returned
	 * by the collection's iterator.
	 *  
	 * @param collection the collection whose elements are to be placed
	 * 		  into this list.
	 */
	public ObjectList(Collection<? extends GObj> collection) {
		super(collection.size());
		for (GObj obj : collection)
			this.add(obj);
	}

	public void sendToBack(GObj obj) {
		this.remove(obj);
		this.add(0, obj);
	}

	public void bringToFront(GObj obj) {
		this.remove(obj);
		this.add(obj);
	}

	public void bringForward(GObj obj, int step) {
		int objIndex = this.indexOf(obj);

		if (objIndex + step < this.size()) {
			this.remove(obj);
			this.add(objIndex + step, obj);
		}
	}

	public void sendBackward(GObj obj, int step) {
		int objIndex = this.indexOf(obj);

		if (objIndex - step >= 0) {
			this.remove(obj);
			this.add(objIndex - step, obj);
		}
	}


	public GObj checkInside(int x, int y) {
		return checkInside(x, y, null);
	}

	public GObj checkInside(int x, int y, GObj asker) {
		for (int i = this.size() - 1; i >= 0; i--) {
			GObj obj = this.get(i);
			if (obj.contains(x, y) && obj != asker) {
				return obj;
			}
		}
		return null;
	}

	public void selectObjectsInsideBox(int x1, int y1, int x2, int y2, boolean appendSelection) {
		for (GObj obj : this) {
		    //select that are inside and deselect if outside the box
		    obj.setSelected( obj.isInside(x1, y1, x2, y2) 
		            || ( appendSelection && obj.isSelected() ) );
		}
	}

	public void updateSize(float newXSize, float newYSize) {
		for (GObj obj: this) {
			obj.setXsize(obj.getXsize() * newXSize);
			obj.setYsize(obj.getYsize() * newYSize);
			obj.setX((int) (obj.getX() * newXSize));
			obj.setY((int) (obj.getY() * newYSize));
		}
	}

	public void clearSelected() {
		for (GObj obj : this) {
			obj.setSelected(false);
		}
	}

	public ArrayList<GObj> getSelected() {
		ArrayList<GObj> a = new ArrayList<GObj>();
		for (GObj obj : this) {
			if (obj.isSelected()) {
				a.add(obj);
			}
		}
		return a;
	}

    /**
     * Returns the number of selected objects.
     * @return the number of selected objects
     */
    public int getSelectedCount() {
        int count = 0;
        for (GObj obj : this) {
            if (obj.isSelected()) {
                count++;
            }
        }
        return count;
    }

	/**
	 * Recalculates start and end points of relation objects and dimensions.
	 */
	public void updateRelObjs() {
        RelObj obj;
		for (GObj o : this) {
			if (o instanceof RelObj) {
                obj = (RelObj) o;

                Point start = VMath.getRelClassStartPoint(obj.getStartPort(),
                		obj.getEndPort());
                
                Point end = VMath.getRelClassStartPoint(obj.getEndPort(),
                		obj.getStartPort());

                obj.setEndPoints(start, end);
			}
		}
	}

	/**
	 * Finds and returns relation objects that are not connected at both ends.
	 *  
	 * @return collections of excess relations
	 */
	public Collection<RelObj> getExcessRels() {
		Collection<RelObj> toBeRemoved = new ArrayList<RelObj>();
		for (GObj obj : this) {
			if (obj instanceof RelObj) {
				RelObj ro = (RelObj) obj;
				if (!contains(ro.getStartPort().getObject()) 
						|| !contains(ro.getEndPort().getObject())) {
					toBeRemoved.add(ro);
				}
			}
		}
		return toBeRemoved;
	}

	public int controlRectContains(int x, int y) {
		int corner;
		for (GObj obj: this) {
			corner = obj.controlRectContains(x, y);
			if (corner != 0) {
				return corner;
			}
		}
		return 0;
	}

	public Port getPort(String objName, String portId) {
		for (GObj obj : this) {
			if (obj.getName().equals(objName)) {
				for (Port port : obj.getPortList()) {
					if (port.getId() != null) {
						if (port.getId().equals(portId)) {
							return port;
						}
					} else if (port.getName().equals(portId)) {
						return port;
					}
				}
			}
		}

		return null;
	}

	/**
	 * Returns the topmost port that contains the specified point.
	 * 
	 * @param x
	 *            X coordinate
	 * @param y
	 *            Y coordinate
	 * @return a port or null if there is no port
	 */
	public Port getPort(int x, int y) {
		return getPort(x, y, null);
	}
	
	/**
	 * Returns the topmost port not belonging to the object asker that contains
	 * the specified point.
	 * 
	 * @param x
	 *            X coordinate
	 * @param y
	 *            Y coordinate
	 * @param asker
	 *            the owner of the ports which are ignored, null if all ports
	 *            should be checked
	 * @return a port or null if there is no port not belonging to the asker
	 */
	public Port getPort(int x, int y, GObj asker) {
		Port port = null;
		GObj obj = checkInside(x, y, asker);
		if (obj != null)
			port = obj.portContains(x, y);
		return port;
	}

	/**
	 * Finds and returns an object by name.
	 * @param name the name to be searched
	 * @return object named name or null
	 */
	public GObj getByName(String name) {
		if (name == null)
			return null;

		for (int i = 0; i < size(); i++) {
			GObj obj = get(i);
			if (name.equals(obj.getName()))
				return obj;
		}
		return null;
	}

	/**
	 * Checks uniqueness of an object name.
	 * @param name the name to be ckecked
	 * @param asker the object to be ignored, can be null
	 * @return true, if there is no object but the asker having the
	 * specified name; false otherwise.
	 */
	public boolean isUniqueName(String name, GObj asker) {
		for (GObj obj : this)
			if (obj != asker && name.equals(obj.getName()))
				return false;

		return true;
	}

    /**
     *  Adds GObj to the list.
     *  If it is a RelObj, push it into the beginning of the list,
     *  i.e. "sendToBack". This fixes the bug when a relClass
     *  needs to be connected to a port with already existing
     *  connection to another relClass.
     */
    @Override
    public boolean add( GObj e ) {
        if(e instanceof RelObj)
            super.add( 0, e );
        else 
            super.add( e );
        
        return true;
    }

    public static ObjectList unfold(ObjectList objects) {
        ObjectList objects2 = new ObjectList();
        GObj obj;

        for (int i = 0; i < objects.size(); i++) {
            obj = objects.get(i);
            objects2.addAll(obj.getComponents());
        }
        return objects2;
    }

    public ObjectList unfold() {
        return ObjectList.unfold(this);
    }
}
