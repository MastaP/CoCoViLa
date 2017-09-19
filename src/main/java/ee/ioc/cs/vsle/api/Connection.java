package ee.ioc.cs.vsle.api;

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

/**
 * The interface of connections between scheme objects that is exposed
 * to the generated programs through the {@code ProgramContext} class.
 * @see ee.ioc.cs.vsle.api.ProgramContext
 * @see ee.ioc.cs.vsle.api.Scheme
 */
public interface Connection {
    public Port getBeginPort();
    public Port getEndPort();
}
