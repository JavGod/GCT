package model;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Set;

import functions.CompilerTool;
import functions.TestGenerator;
import instrument.Instrumentator;
import instrument.InstrumentedMethod;
import parameters.Parameters;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import utils.SpoonUtils;
import utils.StoreFile;
import utils.Utils;

public class UTGenerator
{
	// R. Pawlak, C. Noguera, and N. Petitprez. Spoon:
	// Program analysis and transformation in java.
	// Technical Report 5901, INRIA, 2006.

	private Set<MethodToSelect>	_methods;
	private String				_javaFilePath;
	private String				_packetClass;
	private TestGenerator		_testClass;

	public enum TEMP_FILES
	{
		BORRAR, MANTENER
	};

	private TEMP_FILES _borrarTemporales;

	public UTGenerator(String javaFilePath, Set<MethodToSelect> methods, TEMP_FILES option)
	{
		_javaFilePath = javaFilePath;
		_borrarTemporales = option;
		_packetClass = Utils.getPackageName(_javaFilePath);
		_methods = methods;
		// Copio el archivo java a un directorio temporal
		try
		{
			StoreFile.copyFile(javaFilePath,
					Parameters.TMP_PATH_JAVA_PREPROCESS_CLASS + File.separator + new File(javaFilePath).getName());
		} catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public String generarCasos(int k) throws Exception
	{
		_testClass = new TestGenerator(Utils.getSimpleJavaFileName(_javaFilePath), this._packetClass);
		CtClass<?> ctInstrumentedClass = null;

		System.out.println("Preprocesando...");
		// XXXXXXX: Primero hago un preprocesamiento de cada método
		for (MethodToSelect M : this._methods)
		{
			if (!M.isSelected())
				continue;

			CtMethod<?> actualMethod = M.getCtMethod();

			try
			{
				instrumentPreProcess(actualMethod, k);
			} catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		System.out.println("Instrumentando...");
		// XXXXXXX: Después instrumento los métodos a partir del
		// preprocesamiento anterior
		Instrumentator instrumentator = new Instrumentator(Parameters.TMP_PATH_JAVA_PREPROCESS_CLASS);
		for (MethodToSelect M : this._methods)
		{
			if (!M.isSelected())
				continue;

			CtMethod<?> actualMethod = M.getCtMethod();

			List<CtTypeReference<?>> typeReferences = SpoonUtils.getTypeReferences(actualMethod);
			instrumentator.instrumentMethod(actualMethod.getSimpleName(), typeReferences);
		}
		ctInstrumentedClass = instrumentator.getInstrumentedClass();

		// XXXXXXX: Guardo la clase instrumentada
		storeClass(ctInstrumentedClass, Parameters.TMP_PATH_JAVA_INSTRUMENTED_CLASS);

		// XXXXXXX: Compilo la clase instrumentada y la cargo
		Class<?> classInstrumented = null;
		try
		{
			String qualifiedClassName = Parameters.getPackageInstrumented() + "." + ctInstrumentedClass.getSimpleName();
			String sourceClass = "package " + Parameters.getPackageInstrumented() + ";\n"
					+ ctInstrumentedClass.toString();
			classInstrumented = CompilerTool.CompileAndGetClass(qualifiedClassName, sourceClass,
					Parameters.TMP_PATH_COMPILED_CLASS);
		} catch (ClassNotFoundException e)
		{
			// TODO: tratar excepcion
			e.printStackTrace();
		} catch (MalformedURLException e)
		{
			// TODO: tratar excepcion
			e.printStackTrace();
		}

		System.out.println("Generando casos...");
		// XXXXXX: Ejecuto cada método instrumentado y guardo los valores
		// obtenidos por el solver
		InstrumentedMethod instrumentedMethod = new InstrumentedMethod(classInstrumented);
		for (MethodToSelect M : this._methods)
		{
			if (!M.isSelected())
				continue;

			CtMethod<?> actualMethod = M.getCtMethod();

			// XXXXXX: Obtengo los valores para testear el método
			List<Object[]> inputsGenerated = null;
			try
			{
				Class<?>[] parameterTypes = SpoonUtils.getParametersTypes(actualMethod);
				inputsGenerated = instrumentedMethod.generateInputs(actualMethod.getSimpleName(), parameterTypes);
			} catch (Exception e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// XXXXXX: creo los casos de test para los inputs generados
			for (Object[] inputs : inputsGenerated)
			{
				_testClass.addMethodTest(M.getCtMethod().getSimpleName(), M.getCtMethod().getType().getSimpleName(),
						inputs, SpoonUtils.getParametersTypes(M.getCtMethod()));
			}
		}

		// Borro las carpeta y clases que creé en el preprocesamiento e
		// instrumentación
		if (_borrarTemporales.compareTo(TEMP_FILES.BORRAR) == 0)
		{
			Utils.delete(new File(Parameters.TMP_PATH));
		}

		// XXXXXX: Devuelvo el String con la clase que contiene los casos de
		// test
		return _testClass.getGenerateTestClass();
	}

	private void instrumentPreProcess(CtMethod<?> actualMethod, int k) throws Exception
	{
		Instrumentator instrumentator = new Instrumentator(Parameters.TMP_PATH_JAVA_PREPROCESS_CLASS);

		List<CtTypeReference<?>> typeReferences = SpoonUtils.getTypeReferences(actualMethod);

		if (instrumentator.preProcess(actualMethod.getSimpleName(), typeReferences, k))
		{
			storeClass(instrumentator.getInstrumentedClass(), Parameters.TMP_PATH_JAVA_PREPROCESS_CLASS);
			instrumentPreProcess(actualMethod, k);
			return;
		}

		if (instrumentator.preProcessLoop(actualMethod.getSimpleName(), typeReferences, k))
		{
			storeClass(instrumentator.getInstrumentedClass(), Parameters.TMP_PATH_JAVA_PREPROCESS_CLASS);
			instrumentPreProcess(actualMethod, k);
			return;
		}

	}

	public Integer getGeneratedMethodsCount()
	{
		return this._testClass.getGeneratedMethodsCount();
	}

	private void storeClass(CtClass<?> ctClas, String pathToSave) throws IOException
	{
		String className = ctClas.getSimpleName();

		String Stringclass = "package " + Utils.getPackageName(pathToSave) + ";\n" + ctClas.toString();
		StoreFile sf = new StoreFile(pathToSave + "/", Parameters.JAVA_EXTENSION, Stringclass, className,
				StoreFile.CHARSET_UTF8);
		sf.store();
	}

}
