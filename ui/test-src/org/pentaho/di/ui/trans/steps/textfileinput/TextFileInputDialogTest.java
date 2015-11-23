package org.pentaho.di.ui.trans.steps.textfileinput;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.reflect.FieldUtils;
import org.eclipse.swt.widgets.Shell;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.pentaho.di.core.BlockingRowSet;
import org.pentaho.di.core.KettleEnvironment;
import org.pentaho.di.core.Props;
import org.pentaho.di.core.RowSet;
import org.pentaho.di.core.fileinput.FileInputList;
import org.pentaho.di.core.playlist.FilePlayListAll;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.core.row.value.ValueMetaString;
import org.pentaho.di.core.vfs.KettleVFS;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.errorhandling.FileErrorHandler;
import org.pentaho.di.trans.steps.StepMockUtil;
import org.pentaho.di.trans.steps.textfileinput.*;
import org.pentaho.di.ui.core.PropsUI;
import org.pentaho.di.ui.core.widget.TableView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by jadametz on 9/9/15.
 */
public class TextFileInputDialogTest {

  private static boolean changedPropsUi;

  @BeforeClass
  public static void initKettle() throws Exception {
    KettleEnvironment.init();
  }

  @BeforeClass
  public static void hackPropsUi() throws Exception {
    Field props = getPropsField();
    if ( props == null ) {
      throw new IllegalStateException( "Cannot find 'props' field in " + Props.class.getName() );
    }

    Object value = FieldUtils.readStaticField( props, true );
    if ( value == null ) {
      PropsUI mock = mock( PropsUI.class );
      FieldUtils.writeStaticField( props, mock, true );
      changedPropsUi = true;
    } else {
      changedPropsUi = false;
    }
  }

  @AfterClass
  public static void restoreNullInPropsUi() throws Exception {
    if ( changedPropsUi ) {
      Field props = getPropsField();
      FieldUtils.writeStaticField( props, null, true );
    }
  }

  private static Field getPropsField() {
    return FieldUtils.getDeclaredField( Props.class, "props", true );
  }

  @Test
  public void testMinimalWidth_PDI_14253() throws Exception {
    final String virtualFile = "ram://pdi-14253.txt";
    KettleVFS.getFileObject( virtualFile ).createFile();

    final String content = "r1c1,  r1c2\nr2c1  ,  r2c2  ";
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    bos.write( content.getBytes() );

    OutputStream os = KettleVFS.getFileObject( virtualFile ).getContent().getOutputStream();
    IOUtils.copy( new ByteArrayInputStream( bos.toByteArray() ), os );
    os.close();

    TextFileInputMeta meta = new TextFileInputMeta();
    meta.setLineWrapped( false );
    meta.setInputFields( new TextFileInputField[]{
      new TextFileInputField( "col1", -1, -1 ),
      new TextFileInputField( "col2", -1, -1 )
    } );
    meta.setFileCompression( "None" );
    meta.setFileType( "CSV" );
    meta.setHeader( false );
    meta.setNrHeaderLines( -1 );
    meta.setFooter( false );
    meta.setNrFooterLines( -1 );

    TextFileInputData data = new TextFileInputData();
    data.setFiles( new FileInputList() );
    data.getFiles().addFile( KettleVFS.getFileObject( virtualFile ) );

    data.outputRowMeta = new RowMeta();
    data.outputRowMeta.addValueMeta( new ValueMetaString( "col1" ) );
    data.outputRowMeta.addValueMeta( new ValueMetaString( "col2" ) );

    data.dataErrorLineHandler = mock( FileErrorHandler.class );
    data.fileFormatType = TextFileInputMeta.FILE_FORMAT_UNIX;
    data.separator = ",";
    data.filterProcessor = new TextFileFilterProcessor( new TextFileFilter[ 0 ] );
    data.filePlayList = new FilePlayListAll();

    TextFileInputDialog dialog =
        new TextFileInputDialog( mock( Shell.class ), meta, mock( TransMeta.class ), "TFIMinimalWidthTest" );
    TableView tv = mock( TableView.class );
    when( tv.nrNonEmpty() ).thenReturn( 0 );

    // click the Minimal width button
    dialog.setMinimalWidth( tv );

    RowSet output = new BlockingRowSet( 5 );
    TextFileInput input = StepMockUtil.getStep( TextFileInput.class, TextFileInputMeta.class, "test" );
    input.setOutputRowSets( Collections.singletonList( output ) );
    while ( input.processRow( meta, data ) ) {
      // wait until the step completes executing
    }

    Object[] row1 = output.getRowImmediate();
    assertRow( row1, "r1c1", "r1c2" );

    Object[] row2 = output.getRowImmediate();
    assertRow( row2, "r2c1", "r2c2" );

    KettleVFS.getFileObject( virtualFile ).delete();

  }

  private static void assertRow( Object[] row, Object... values ) {
    assertNotNull( row );
    assertTrue( String.format( "%d < %d", row.length, values.length ), row.length >= values.length );
    int i = 0;
    while ( i < values.length ) {
      assertEquals( values[ i ], row[ i ] );
      i++;
    }
    while ( i < row.length ) {
      assertNull( row[ i ] );
      i++;
    }
  }
}
