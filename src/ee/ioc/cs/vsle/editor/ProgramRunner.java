package ee.ioc.cs.vsle.editor;

import static ee.ioc.cs.vsle.util.TypeUtil.*;

import java.awt.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.tree.*;

import ee.ioc.cs.vsle.api.*;
import ee.ioc.cs.vsle.api.Scheme;
import ee.ioc.cs.vsle.ccl.*;
import ee.ioc.cs.vsle.event.*;
import ee.ioc.cs.vsle.synthesize.*;
import ee.ioc.cs.vsle.table.*;
import ee.ioc.cs.vsle.util.*;
import ee.ioc.cs.vsle.util.FileFuncs.*;
import ee.ioc.cs.vsle.vclass.*;

/**
 * This class is used for invoking planning, compiling and other procedures. It
 * communicates with ProgramTextEditor by means of events through EventQueue
 * thread. Planning and invocation of compiled code is done in the separate
 * threads.
 */
public class ProgramRunner {

    private final static Object s_lock = new Object();
    private volatile boolean isWorking = false;

    private long m_id;
    private ProgramRunnerEventListener m_lst = new ProgramRunnerEventListener();

    private Object genObject;

    private ObjectList objects;
    private ClassList classList;
    private List<Var> assumptions = new ArrayList<Var>();
    private Object[] arguments;
    private String mainClassName = new String();
    private ISchemeContainer schemeContainer;
    private GenStorage storage;

    public ProgramRunner( ISchemeContainer canvas ) {

        m_id = System.currentTimeMillis();
        ProgramRunnerEvent.registerListener( m_lst );

        schemeContainer = canvas;

        schemeContainer.registerRunner( m_id );

        //TODO tmp:parse tables each time new runner is created
        TableManager.updateTables( schemeContainer.getPackage() );
        
        updateFromCanvas();
    }

    private void updateFromCanvas() {
        objects = GroupUnfolder.unfold( schemeContainer.getObjects() );
    }

    public void destroy() {

        RunningThreadManager.removeThread( getId(), true );
        
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                schemeContainer.unregisterRunner( m_id );
                schemeContainer = null;
            }
        } );

        if ( m_lst != null ) {

            ProgramRunnerEvent.unregisterListener( m_lst );

            m_lst = null;

        }
        
        ProgramRunnerFeedbackEvent evt = new ProgramRunnerFeedbackEvent( this, m_id, ProgramRunnerFeedbackEvent.DISPOSE,
                null );

        EventSystem.queueEvent( evt );
    }

    private String getSpec() {

        updateFromCanvas();

        return SpecGenFactory.getInstance().getCurrentSpecGen().generateSpec( schemeContainer.getScheme(),
                schemeContainer.getPackage().getPackageClassName() );
    }

    private Object[] getArguments() throws Exception {
        if ( getAssumptions().isEmpty() ) {
            return new Object[0];
        }

        if ( arguments == null ) {
            ProgramAssumptionsDialog ass = new ProgramAssumptionsDialog( null, mainClassName, getAssumptions() );

            if ( ass.isOK ) {
                arguments = ass.getArgs();
            } else {
                throw new Exception( "Unable to run the program: assumptions undefined" );
            }
        }

        return arguments;
    }

    private void compile( String genCode ) {

        arguments = null;

        try {
            GenStorage fs = getStorage();
            Synthesizer.makeProgram( genCode, classList, mainClassName, schemeContainer.getWorkDir(), fs );
            genObject = compile( fs, schemeContainer, mainClassName );
        } catch ( NoClassDefFoundError e ) {
            JOptionPane.showMessageDialog( null, "Class not found:\n"
                    + e.getMessage(), "Execution error",
                    JOptionPane.ERROR_MESSAGE );
            e.printStackTrace( System.err );
        } catch (CompileException ce) {
            ErrorWindow.showErrorMessage("Compilation failed:\n " + ce.toString());
        } catch (SpecParseException e) {
            ErrorWindow.showErrorMessage("Compilation failed:\n " + e.toString());
        } catch (Exception e) {
            ErrorWindow.showErrorMessage("Compilation failed:\n " + e.toString());
        } 
    }
    
    private static Object compile( GenStorage fs, ISchemeContainer cont,
            String className ) throws ClassNotFoundException,
            InstantiationException, IllegalAccessException,
            IllegalArgumentException, SecurityException,
            InvocationTargetException, NoSuchMethodException {

        ClassLoader classLoader = cont.getPackage().newRunnerClassLoader( fs );
        Class<?> pc = classLoader.loadClass( CCL.PROGRAM_CONTEXT );
        Class<?> clas = classLoader.loadClass( className );
        Object object = clas.newInstance();
        pc.getMethod( "setScheme", Scheme.class ).invoke( null,
                cont.getScheme() );

        return object;
    }

    private void generateProgramSource( final ProgramRunnerEvent event, final int operation, final boolean compute ) {

        new Thread( "PlanningThread_" + System.currentTimeMillis() ) {

            @Override
            public void run() {

                setWorking( true );

                String programSource = null;

                try {
                    String spec = ( event.getSpecText() != null ) ? event.getSpecText() : getSpec();

                    RunningThreadManager.addThread( ProgramRunner.this.getId(), this );

                    programSource = compute( spec, compute );
                    
                    if ( programSource == null )
                        return;

                } finally {
                    setWorking( false );
                    RunningThreadManager.removeThread( ProgramRunner.this.getId(), false );
                }

                if ( event.isRequestFeedback() ) {
                    ProgramRunnerFeedbackEvent evt = new ProgramRunnerFeedbackEvent( this, event.getId(),
                            ProgramRunnerFeedbackEvent.TEXT_PROGRAM, programSource );

                    EventSystem.queueEvent( evt );
                }

                ProgramRunnerEvent newEvent = new ProgramRunnerEvent( event.getSource(), event.getId(), operation );

                newEvent.setProgramText( programSource );

                EventSystem.queueEvent( newEvent );
            }

        }.start();

    }

    private String compute( String fullSpec, boolean computeAll ) {

        try {
            foundVars.clear();

            mainClassName = SpecParser.getClassName( fullSpec );

            if ( RuntimeProperties.isLogInfoEnabled() )
                db.p( "Computing " + mainClassName );

            Set<String> schemeObjects = new HashSet<String>();

            for ( GObj gObj : schemeContainer.getObjects() ) {
                schemeObjects.add( gObj.getName() );
            }

            classList = SpecParser.parseSpecification( fullSpec, mainClassName, schemeObjects, schemeContainer.getWorkDir() );
            getAssumptions().clear();

            return Synthesizer.makeProgramText( fullSpec, computeAll, classList, mainClassName, this );
            
        } catch ( Throwable ex ) {
            reportException(ex);
        }

        return null;
    }

    private static void reportException(Throwable e) {
        String msg;
        if( e instanceof UnknownVariableException ) {
            UnknownVariableException uve = (UnknownVariableException)e;
            String line = uve.getLine();
            msg = "Fatal error: variable " + uve.getMessage() + " not declared"
                    + ( line != null ? ", line: " + line : "" );
            db.p( msg );
            ErrorWindow.showErrorMessage( msg );
        }
        else if( e instanceof  LineErrorException ) {
            LineErrorException lee = (LineErrorException)e;
            msg = "Syntax error on line '" + lee.getMessage() + "'";
            db.p( msg );
            ErrorWindow.showErrorMessage( msg );
        }
        else if( e instanceof  EquationException ) {
            EquationException ee = (EquationException)e;
            msg = "EquationException " + ee.getMessage();
            ErrorWindow.showErrorMessage( msg );
        }
        else if( e instanceof  MutualDeclarationException ) {
            MutualDeclarationException lee = (MutualDeclarationException)e;
            msg = "Mutual recursion in specifications, between classes "
                + lee.getMessage();
            db.p( msg );
            ErrorWindow.showErrorMessage( msg );
        }
        else if( e instanceof  SpecParseException ) {
            SpecParseException spe = (SpecParseException)e;
            String line = spe.getLine();
            msg = "Specification parsing error: " + spe.getMessage() 
                + (line != null ? ", line: " + line : ""); 
            db.p( msg );
            ErrorWindow.showErrorMessage( msg );
        }
        else {
            ErrorWindow.showErrorMessage( e.getMessage() );
            e.printStackTrace();
        }
    }

    private Collection<String> watchableFields() {
        GObj obj;

        objects = GroupUnfolder.unfold( objects );
        Collection<String> watchFields = new TreeSet<String>();

        for ( int i = 0; i < objects.size(); i++ ) {
            obj = objects.get( i );
            for ( ClassField field : obj.getFields() ) {
                if ( field.isWatched() ) {
                    watchFields.add( obj.getName() + "." + field.getName() );
                }
            }
        }
        return watchFields;
    }

    private Set<Var> foundVars = new TreeSet<Var>( new Comparator<Var>() {

        public int compare( Var v1, Var v2 ) {

            return v1.toString().compareTo( v2.toString() );
        }
    } );

    public void addFoundVars( Collection<Var> col ) {
        foundVars.addAll( col );
    }

    private String printFoundVars() {

        StringBuilder result = new StringBuilder( "----------- Found Vars -----------\n" );

        for ( Var var : foundVars ) {

            if ( var.getField().isAlias() || var.getField().isVoid() ) {
                continue;
            }

            try {
                appendVarStringValue( var.getFullName(), result, true );
            } catch ( Exception e ) {
            }
        }

        result.append( "----------------------------------\n" );
        result.append( "\n" );

        return result.toString();
    }

    private void showComputedValues( final String rootVarName ) {

        final DefaultMutableTreeNode varRoot = new DefaultMutableTreeNode();
        final DefaultMutableTreeNode aliasRoot = new DefaultMutableTreeNode( "Aliases" );

        for ( Var var : foundVars ) {

            if ( var.getField().isVoid() || CodeGenerator.SPEC_OBJECT_NAME.equals( var.getName() ) ) {
                continue;
            }

            String varname;
            if ( ( varname = var.getFullName() ).startsWith( rootVarName )
                    || ( TypeUtil.TYPE_THIS.equals( rootVarName ) && TypeUtil.TYPE_THIS.equals( var.getObject() ) ) ) {
                try {
                    if ( var.getField().isAlias() ) {
                        DefaultMutableTreeNode aliasNode = new DefaultMutableTreeNode( var.getFullName() );
                        aliasRoot.add( aliasNode );
                        fillAliasTree( aliasNode, var );
                    } else {
                        StringBuilder result = new StringBuilder();
                        appendVarStringValue( varname, result, false );
                        DefaultMutableTreeNode varNode = new DefaultMutableTreeNode( result.toString() );
                        varRoot.add( varNode );
                    }
                } catch ( Exception e ) {
                }
            }
        }

        if ( aliasRoot.getChildCount() > 0 ) {
            varRoot.add( aliasRoot );
        }

        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                JDialog dialog = new JDialog( Editor.getInstance(), rootVarName );
                dialog.setLocationRelativeTo( Editor.getInstance() );
                dialog.getContentPane().setLayout( new BorderLayout() );

                JTree tree = new JTree( varRoot );
                tree.setRootVisible( false );
                JScrollPane treeView = new JScrollPane( tree );

                dialog.getContentPane().add( treeView, BorderLayout.CENTER );
                dialog.setSize( 350, 400 );
                dialog.setDefaultCloseOperation( JDialog.DISPOSE_ON_CLOSE );
                dialog.setVisible( true );
            }
        } );
    }

    private void fillAliasTree( DefaultMutableTreeNode parent, Var alias ) throws IllegalArgumentException, SecurityException,
            IllegalAccessException, NoSuchFieldException {

        for ( Var var : alias.getChildVars() ) {

            DefaultMutableTreeNode varNode = new DefaultMutableTreeNode();
            parent.add( varNode );

            if ( var.getField().isAlias() ) {
                varNode.setUserObject( var.getFullName() );
                fillAliasTree( varNode, var );
            } else if ( var.getField().isVoid() ) {
                varNode.setUserObject( var.getFullName() );
            } else {
                varNode.setUserObject( var.getFullName().concat( " = " ).concat( getVarValueAsString( var.getFullName() ) ) );
            }
        }

    }

    private String getAliasStringValue( Var alias ) throws IllegalArgumentException, SecurityException, IllegalAccessException,
            NoSuchFieldException {

        StringBuilder result = new StringBuilder( "[" );

        for ( Var var : alias.getChildVars() ) {

            if ( var.getField().isAlias() ) {
                result.append( getAliasStringValue( var ) ).append( ", " ).append( "\n" );
                continue;
            } else if ( var.getField().isVoid() ) {
                result.append( var.getName() );
            } else {
                result.append( var.getFullName() ).append( " = " ).append( getVarValueAsString( var.getFullName() ) );
            }

            result.append( ", " );
        }

        // remove last ", "
        int length = result.length();
        result.delete( length - 2, length );

        result.append( "]" );// .append( "\n" );

        return result.toString();
    }

    private String getVarValueAsString( String fullName ) throws IllegalArgumentException, IllegalAccessException, SecurityException,
            NoSuchFieldException {

        Class<?> clas;
        Field f;

        StringTokenizer st = new StringTokenizer( fullName, "." );
        Object obj = genObject;
        clas = genObject.getClass();

        while ( st.hasMoreElements() ) {

            String s = st.nextToken();

            // show values of variables declared in superclasses as well
            f = clas.getField( s );// getDeclarField(s);

            if ( st.hasMoreElements() ) {
                clas = f.getType();
                obj = f.get( obj );
            } else {
                Class<?> c = f.getType();

                if ( c.toString().equals( TYPE_INT ) ) {
                    return Integer.toString( f.getInt( obj ) );
                } else if ( c.toString().equals( TYPE_LONG ) ) {
                    return Long.toString( f.getLong( obj ) );
                } else if ( c.toString().equals( TYPE_DOUBLE ) ) {
                    return Double.toString( f.getDouble( obj ) );
                } else if ( c.toString().equals( TYPE_BOOLEAN ) ) {
                    return Boolean.toString( f.getBoolean( obj ) );
                } else if ( c.toString().equals( TYPE_CHAR ) ) {
                    return Character.toString( f.getChar( obj ) );
                } else if ( c.toString().equals( TYPE_FLOAT ) ) {
                    return Float.toString( f.getFloat( obj ) );
                } else if ( c.toString().equals( TYPE_SHORT ) ) {
                    return Short.toString( f.getShort( obj ) );
                } else if ( c.toString().equals( TYPE_BYTE ) ) {
                    return Byte.toString( f.getByte( obj ) );
                } else {
                    Object o = f.get( obj );
                    if ( o.getClass().isArray() ) {
                        String result = "[";
                        for ( int i = 0; i < Array.getLength( o ); i++ ) {
                            if(i > 0)
                                result += ", ";
                            result += Array.get( o, i );
                        }
                        return result + "]";
                    }
                    return o.toString();
                }
            }
        }

        return null;
    }

    private void appendVarStringValue( String fullName, StringBuilder result, boolean showRoot ) throws SecurityException,
            NoSuchFieldException, IllegalArgumentException, IllegalAccessException {

        String value;

        if ( ( value = getVarValueAsString( fullName ) ) != null ) {

            int idx;

            if ( !showRoot && ( idx = fullName.indexOf( "." ) ) > -1 ) {
                fullName = fullName.substring( idx + 1, fullName.length() );
            }

            result.append( fullName ).append( " = " ).append( value ).append( "\n" );
        }
    }

    private void propagate() {
        try {

            if ( genObject == null || isWorking() )
                return;

            final Class<?> clas = genObject.getClass();

            Field fieldOfGobj, fieldOfCf;
            Object lastObj;
            Class<?> classOfGobj;
            String namePrefix;
            
            for ( GObj gObj : objects ) {

                // superclass object is a special case that has no field
                // declaration in the generated code so we have to skip it here
                if ( gObj.isSuperClass() ) {
                    classOfGobj = clas;
                    lastObj = genObject;
                    namePrefix = gObj.getName() + ".";
                } else {
                    fieldOfGobj = clas.getDeclaredField( gObj.getName() );
                    classOfGobj = fieldOfGobj.getType();
                    lastObj = fieldOfGobj.get( genObject );
                    namePrefix = "";
                }

                for ( ClassField cf : gObj.getFields() ) {

                    if ( cf.isAlias() || cf.isInput() ) {
                        continue;
                    }

                    fieldOfCf = classOfGobj.getField( cf.getName() );

                    boolean varIsComputed = false;

                    for ( Var var : foundVars ) {

                        String fullName = namePrefix + var.getFullName();
                        String fieldName = gObj.getName() + "." + cf.getName();
                        
                        if ( fullName.equals( fieldName ) ) {
                            varIsComputed = true;
                            break;
                        }
                    }

                    if ( varIsComputed ) {

                        String typeOfCf = fieldOfCf.getType().toString();

                        if ( typeOfCf.equals( TYPE_INT ) ) {

                            cf.setValue( Integer.toString( fieldOfCf.getInt( lastObj ) ) );

                        } else if ( typeOfCf.equals( TYPE_DOUBLE ) ) {

                            cf.setValue( Double.toString( fieldOfCf.getDouble( lastObj ) ) );

                        } else if ( typeOfCf.equals( TYPE_BOOLEAN ) ) {

                            cf.setValue( Boolean.toString( fieldOfCf.getBoolean( lastObj ) ) );

                        } else if ( typeOfCf.equals( TYPE_CHAR ) ) {

                            cf.setValue( Character.toString( fieldOfCf.getChar( lastObj ) ) );

                        } else if ( typeOfCf.equals( TYPE_FLOAT ) ) {

                            cf.setValue( Float.toString( fieldOfCf.getFloat( lastObj ) ) );

                        } else if ( typeOfCf.equals( TYPE_LONG ) ) {

                            cf.setValue( Long.toString( fieldOfCf.getLong( lastObj ) ) );

                        } else if ( typeOfCf.equals( TYPE_SHORT ) ) {

                            cf.setValue( Short.toString( fieldOfCf.getShort( lastObj ) ) );

                        } else if ( typeOfCf.equals( TYPE_BYTE ) ) {

                            cf.setValue( Byte.toString( fieldOfCf.getByte( lastObj ) ) );

                        } else {// it is type object
                            Object o = fieldOfCf.get( lastObj );
                            
                            if( o != null ) {
                                if( o.getClass().isArray() ) { 
                                    String result = "";
                                    for ( int i = 0; i < Array.getLength( o ); i++ ) {
                                        result += Array.get( o, i ) + TypeUtil.ARRAY_TOKEN;
                                    }
                                    cf.setValue( result );
                                } else {
                                    cf.setValue( o.toString() );
                                }
                            }
                        }
                    }
                }
            }
        } catch ( Exception e ) {
            db.p( "Error propagating value: " + e.getClass().getCanonicalName() + " : " + e.getMessage() );
            if ( RuntimeProperties.isLogDebugEnabled() ) {
                e.printStackTrace( System.err );
            }
        } finally {
            schemeContainer.repaint();
        }
    }

    private void run( final boolean sendFeedback, final long id, final boolean doPropagate ) {

        if ( genObject == null )
            return;

        new Thread() {
            
            @Override
            public void run() {
                Thread.currentThread().setName( "RunningThread_" + System.currentTimeMillis() );

                boolean rerun = false;

                try {
                    Object[] args = getArguments();
                    for ( int i = 0; i < args.length; i++ ) {
                        db.p( args[i].getClass() + " " + args[i] );
                    }

                    Class<?> clas = genObject.getClass();
                    
                    ClassLoader cl = clas.getClassLoader();
                    initProgramContext(ProgramRunner.this, cl);
                    Method method = clas.getMethod( "compute", Object[].class );
                    db.p( "Running... ( NB! The thread is alive until the next message --> ) " + Thread.currentThread().getName() );

                    setWorking( true );

                    RunningThreadManager.addThread( ProgramRunner.this.getId(), this );

                    try {
                        method.invoke( genObject, new Object[] { args } );
                    } catch ( InvocationTargetException ex ) {
                        /*
                         * Stacktrace is printed so that there is some feedback
                         * when generated code throws an exception which isn't
                         * caught. Stacktrace is not printed if a thread was
                         * stopped manually or a rerun was requested.
                         */
                        if (ex.getCause() instanceof RerunProgramException) {
                            rerun( schemeContainer );
                            rerun = true;
                        } else if ( ex.getCause() instanceof RunningProgramException ) {
                            ex.getCause().printStackTrace();
                        } else if ( !( ex.getCause() instanceof ThreadDeath )
                                && !(ex.getCause() instanceof TerminateProgramException)) {
                            ex.printStackTrace();
                        }
                    } finally {
                        RunningThreadManager.removeThread( ProgramRunner.this.getId(), false );
                        setWorking( false );
                        db.p( "--> Finished!!! " + Thread.currentThread().getName() );
                    }

                    if ( sendFeedback ) {

                        ProgramRunnerFeedbackEvent evt = new ProgramRunnerFeedbackEvent( this, id, ProgramRunnerFeedbackEvent.TEXT_RESULT,
                                printFoundVars() + printWatchFields() );

                        EventSystem.queueEvent( evt );
                    }

                    if ( doPropagate && !rerun ) {
                        propagate();
                    }

                } catch ( Exception e ) {
                    ErrorWindow.showErrorMessage( e.getMessage() );
                }

            }
        }.start();

    }

    /**
     * Creates new ProgramRunner instance and sends an event for running a program from the given canvas
     * 
     * @param canvas
     */
    public static void rerun( ISchemeContainer canvas ) {
        
        ProgramRunner pr = new ProgramRunner( canvas );
        
        int op = ( RuntimeProperties.isComputeGoal() ? ProgramRunnerEvent.COMPUTE_GOAL : ProgramRunnerEvent.COMPUTE_ALL ) 
            | ProgramRunnerEvent.RUN_NEW
            | ( RuntimeProperties.isPropagateValues() ? ProgramRunnerEvent.PROPAGATE : 0 );

        ProgramRunnerEvent evt = new ProgramRunnerEvent( canvas, pr.getId(), op );
        EventSystem.queueEvent( evt );
    }
    
    public Object[] computeModel(Class<?> context, String[] inputNames,
            String[] outputNames, Object[] inputValues)
    {
        return computeModel( context.getName(), inputNames, outputNames, inputValues, true );
    }
    
    /**
     * Computes a given model and returns the result
     * 
     * @param contextClassName
     * @param inputNames
     * @param outputNames
     * @param inputValues
     * @return
     */
    public Object[] computeModel(String contextClassName, String[] inputNames,
            String[] outputNames, Object[] inputValues, boolean cacheCompiledModel )
    {
        long start = System.currentTimeMillis();
        try {
            Object genObj = null;
            int hash = -1;
            
            if(cacheCompiledModel) {
                hash = calcHashForSubtask( contextClassName, inputNames, outputNames );
                genObj = getFromCache( hash );
            }
            
            if ( genObj == null ) {
                //synthesize
                StringBuilder result = new StringBuilder();
                ClassList classes = new ClassList();
                String generatedClassName = Synthesizer
                        .computeIndependentModel( contextClassName,
                                schemeContainer.getWorkDir(), inputNames,
                                outputNames, classes, result );
                //save generated code
                GenStorage fs = getStorage();
                Synthesizer.makeProgram( result.toString(), classes,
                        generatedClassName, schemeContainer.getWorkDir(), fs );
                //compile
                genObj = compile( fs, schemeContainer, generatedClassName );
                if(cacheCompiledModel)
                    cacheObject( hash, genObj );
            }

            if(genObj == null)
                throw new ComputeModelException( "Unable to compile " + contextClassName );
            //execute
            Class<?> clas = genObj.getClass();        
            ClassLoader cl = clas.getClassLoader();
            initProgramContext(ProgramRunner.this, cl);
            Method method = clas.getMethod( "run", Object[].class );
            return (Object[])method.invoke( genObj, new Object[] { inputValues } );
        } catch ( SpecParseException e ) {
            reportException( e );
            throw new ComputeModelException( "Error computing model " + contextClassName, e );
        } catch ( Exception ex ) {
            ErrorWindow.showErrorMessage( "Computing model failed:\n " + ex.getMessage() );
            throw new ComputeModelException( "Error computing model " + contextClassName, ex );
        } finally {
            if(RuntimeProperties.isLogDebugEnabled())
                db.p( "Computed independent model in " + (System.currentTimeMillis() - start) + "ms." );
        }
    }
    
    private static Map<Integer, Object> objectCache = new Hashtable<Integer, Object>();
    
    private Object getFromCache( int hash ) {
        return objectCache.get( hash );
    }

    private void cacheObject( int hash, Object obj ) {
        objectCache.put( hash, obj );
    }
    
    private int calcHashForSubtask( String name, String[] inputNames,
            String[] outputNames ) {
        return new StringBuilder( name ).append( Arrays.toString( inputNames ) )
                .append( Arrays.toString( outputNames ) ).toString().hashCode();
    }
    
    /**
     * Sets required attributes to ProgramContext before execution
     * 
     * @param runner
     * @param cl
     * @throws ClassNotFoundException
     * @throws IllegalArgumentException
     * @throws SecurityException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws NoSuchMethodException
     */
    private static void initProgramContext( ProgramRunner runner, ClassLoader cl )
            throws ClassNotFoundException, IllegalArgumentException,
            SecurityException, IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {
        
        Class<?> pc = cl.loadClass( CCL.PROGRAM_CONTEXT );
        pc.getMethod( "setThread", Thread.class ).invoke( null,
                Thread.currentThread() );
        pc.getMethod( "setRunnerId", long.class ).invoke( null, runner.getId() );
    }
    
    /**
     * Prints Watch Fields
     * 
     * @return
     */
    private String printWatchFields() {

        StringBuilder result = new StringBuilder( "---------- Watch Fields ----------\n" );

        Collection<String> watchFields = watchableFields();

        for ( String wf : watchFields ) {

            try {
                appendVarStringValue( wf, result, true );
            } catch ( Exception e ) {
            }
        }

        result.append( "----------------------------------\n" );
        result.append( "\n" );

        return result.toString();
    }

    /**
     * @return id of this Runner
     */
    public long getId() {
        return m_id;
    }

    class ProgramRunnerEventListener implements ProgramRunnerEvent.Listener {

        public void onProgramRunnerEvent( final ProgramRunnerEvent event ) {

            if ( event.getId() != m_id ) {
                return;
            }
            
            if ( isWorking() ) {
                
                //kill 'em all even if working
                if ( ( event.getOperation() & ProgramRunnerEvent.DESTROY ) > 0 ) {
                    destroy();
                }
                
                return;
            }

            int operation = event.getOperation();

            if ( ( operation & ProgramRunnerEvent.REQUEST_SPEC ) > 0 ) {

                ProgramRunnerFeedbackEvent evt = new ProgramRunnerFeedbackEvent( this, event.getId(),
                        ProgramRunnerFeedbackEvent.TEXT_SPECIFICATION, getSpec() );

                EventSystem.queueEvent( evt );

                return;
            }

            Boolean computeAll = null;

            if ( ( operation & ProgramRunnerEvent.COMPUTE_ALL ) > 0 ) {
                computeAll = true;
                operation &= ~ProgramRunnerEvent.COMPUTE_ALL;
            } else if ( ( operation & ProgramRunnerEvent.COMPUTE_GOAL ) > 0 ) {
                computeAll = false;
                operation &= ~ProgramRunnerEvent.COMPUTE_GOAL;
            }

            if ( computeAll != null ) {

                generateProgramSource( event, operation, computeAll );
                return;
            }

            if ( ( operation & ProgramRunnerEvent.COMPILE ) > 0 ) {

                compile( event.getProgramText() );
            }

            boolean isPropagated = false;

            if ( ( operation & ProgramRunnerEvent.RUN ) > 0 ) {

                for ( int i = 0; i < event.getRepeat(); i++ ) {
                    try {
                        run( event.isRequestFeedback(), event.getId(), ( ( operation & ProgramRunnerEvent.PROPAGATE ) > 0 ) );
                        isPropagated = true;
                    } catch ( Exception e ) {
                        ErrorWindow.showErrorMessage( e.getMessage() );
                    }
                }
            }

            if ( !isPropagated && ( operation & ProgramRunnerEvent.PROPAGATE ) > 0 ) {
                propagate();
            }

            if ( ( operation & ProgramRunnerEvent.SHOW_VALUES ) > 0 ) {
                showComputedValues( event.getObjectName() );
            }

            if ( ( operation & ProgramRunnerEvent.DESTROY ) > 0 ) {
                destroy();
            }

        }

    }

    /**
     * Indicates if there is a working thread running
     * 
     * @return <code>true</code> if there is a working thread running; <code>false</code> otherwise.
     */
    public boolean isWorking() {

        synchronized ( s_lock ) {
            return isWorking;
        }
    }

    /**
     * Sets the flag to indicate that there is a working thread running
     * 
     * @param isWorking
     */
    public void setWorking( boolean isWorking ) {
        synchronized ( s_lock ) {
            this.isWorking = isWorking;

            ProgramRunnerFeedbackEvent event = new ProgramRunnerFeedbackEvent( this, getId(), isWorking );
            EventSystem.queueEvent( event );
        }
    }

    /**
     * @param assumptions
     *                the assumptions to set
     */
    public void setAssumptions( List<Var> assumptions ) {
        this.assumptions = assumptions;
    }

    /**
     * @return the assumptions
     */
    public List<Var> getAssumptions() {
        return assumptions;
    }

    private GenStorage getStorage() {
        if (storage == null) {
            storage = RuntimeProperties.isDumpGenerated()
                ? new FileSystemStorage(RuntimeProperties.getGenFileDir())
                : new MemoryStorage();
        }
        return storage;
    }
}
