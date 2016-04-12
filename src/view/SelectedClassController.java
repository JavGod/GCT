package view;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import controller.MainApp;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.util.Callback;
import model.MethodToSelect;
import model.SpoonedClass;
import model.UTGenerator;
import spoon.reflect.declaration.CtMethod;
import utils.StoreFile;

public class SelectedClassController
{

	private MainApp						_mainApp;
	@FXML
	private Button						_btnBuscar;
	@FXML
	private Button						_btnGenerarTest;
	@FXML
	private ScrollPane					_scrollPaneMethods;
	@FXML
	private Pane						_paneLoadClass;
	@FXML
	private Pane						_paneMain;
	@FXML
	private ListView<MethodToSelect>	_chkListMethods;
	private SpoonedClass				spoonedClass;
	private String						_filePathJava;
	private ExtensionFilter				javaFilter;
	private final static String			javaExtension	= ".java";

	/**
	 * The constructor. The constructor is called before the initialize()
	 * method.
	 */
	public SelectedClassController()
	{
	}

	/**
	 * Initializes the controller class. This method is automatically called
	 * after the fxml file has been loaded.
	 */
	@FXML
	private void initialize()
	{
		this._paneLoadClass.setDisable(true);
		List<String> extensions = new ArrayList<String>();
		extensions.add("*" + javaExtension);
		javaFilter = new ExtensionFilter("java files(.java)", extensions);

		_chkListMethods
				.setCellFactory(CheckBoxListCell.forListView(new Callback<MethodToSelect, ObservableValue<Boolean>>() {
					@Override
					public ObservableValue<Boolean> call(MethodToSelect param)
					{
						return param.isSelectedProperty();
					}
				}));
	}

	public void openFileChooser()
	{
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Selecciona un archivo .java");

		fileChooser.getExtensionFilters().add(javaFilter);

		File fileClass = fileChooser.showOpenDialog(this._mainApp.getPrimaryStage());

		if (fileClass == null)
			return;

		this._paneLoadClass.setDisable(false);

		// Agrego los métodos de la clase elegida para poder seleccionarlos
		this._filePathJava = fileClass.getAbsolutePath();
		spoonedClass = new SpoonedClass(_filePathJava);
		try
		{
			spoonedClass.loadClass();
		} catch (Exception e)
		{
			ViewUtils.alertException("Se ha producido un error al cargar la clase " + this._filePathJava, e);
			this._paneLoadClass.setDisable(true);
			return;
		}
		this.clearListAndCompleteMethods(spoonedClass.getAllMethods());
	}

	public void generarCasosDeTest()
	{
		Set<MethodToSelect> selectedMethods = new HashSet<>();
		for (MethodToSelect selectedMethod : this._chkListMethods.getItems())
		{
			if (selectedMethod.isSelected())
				selectedMethods.add(selectedMethod);
		}
		if (selectedMethods.isEmpty())
		{
			ViewUtils.alertWarning("Operación no permitida", "Debe seleccionar al menos un método!");
			return;
		}
		UTGenerator generator = new UTGenerator(spoonedClass, selectedMethods);
		String testClass = generator.generarCasos();

		String classToTestName = spoonedClass.getSpoonedClassName();
		String pathToSave = this._filePathJava.replace(classToTestName + javaExtension, "");
		StoreFile sf = new StoreFile(pathToSave, javaExtension, testClass, classToTestName + "Test",
				StoreFile.CHARSET_UTF8);
		try
		{
			sf.store();
			ViewUtils.alertInformation("Se ha almacenado la clase " + classToTestName + "Test",
					"Se han generado correctamente " + generator.getGeneratedMethodsCount()
							+ " métodos y fueron almacenados en el directorio " + pathToSave);
		} catch (IOException e)
		{
			ViewUtils.alertException("Error al guardar la clase con los casos de test (" + classToTestName + "Test)",
					e);
			e.printStackTrace();
			this._paneLoadClass.setDisable(true);
			return;
		}
		this._paneLoadClass.setDisable(true);
	}

	/**
	 * Is called by the main application to give a reference back to itself.
	 * 
	 * @param mainApp
	 */
	public void setMainApp(MainApp mainApp)
	{
		this._mainApp = mainApp;
	}

	private void clearListAndCompleteMethods(Set<CtMethod<?>> methods)
	{
		_chkListMethods.getItems().clear();
		for (CtMethod<?> method : methods)
		{
			_chkListMethods.getItems().add(new MethodToSelect(method));
		}
	}

}
