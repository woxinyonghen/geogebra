package org.geogebra.common.kernel.geos.inputbox;

import org.geogebra.common.kernel.Kernel;
import org.geogebra.common.kernel.StringTemplate;
import org.geogebra.common.kernel.arithmetic.FunctionalNVar;
import org.geogebra.common.kernel.commands.AlgebraProcessor;
import org.geogebra.common.kernel.commands.EvalInfo;
import org.geogebra.common.kernel.geos.GeoInputBox;
import org.geogebra.common.kernel.geos.GeoText;
import org.geogebra.common.kernel.commands.redefinition.RedefinitionRule;
import org.geogebra.common.kernel.commands.redefinition.RedefinitionRules;
import org.geogebra.common.kernel.kernelND.GeoElementND;
import org.geogebra.common.kernel.kernelND.GeoPointND;
import org.geogebra.common.main.App;
import org.geogebra.common.main.MyError;
import org.geogebra.common.main.error.ErrorHandler;
import org.geogebra.common.main.error.ErrorHelper;
import org.geogebra.common.plugin.GeoClass;
import org.geogebra.common.util.debug.Log;

import com.himamis.retex.editor.share.util.Unicode;

/**
 * Updates linked element for an input box from user input
 */
public class InputBoxProcessor {

	private GeoInputBox inputBox;
	private GeoElementND linkedGeo;
	private Kernel kernel;
	private App app;
	private AlgebraProcessor algebraProcessor;
	private ErrorHandler errorHandler;
	private boolean showErrorDialog;

	/**
	 * @param inputBox
	 *            parent input box
	 * @param linkedGeo
	 *            linked element
	 */
	public InputBoxProcessor(GeoInputBox inputBox, GeoElementND linkedGeo) {
		this.inputBox = inputBox;
		this.linkedGeo = linkedGeo;
		this.kernel = inputBox.getKernel();
		this.app = kernel.getApplication();
		this.algebraProcessor = kernel.getAlgebraProcessor();
		this.showErrorDialog = app.getConfig().isShowingErrorDialogForInputBox();
		this.errorHandler = showErrorDialog ? app.getErrorHandler() : ErrorHelper.silent();
	}

	/**
	 * @param inputText
	 *            user input
	 * @param tpl
	 *            template
	 */
	public void updateLinkedGeo(String inputText, StringTemplate tpl) {
		if (!linkedGeo.isLabelSet() && linkedGeo.isGeoText()) {
			((GeoText) linkedGeo).setTextString(inputText);
			return;
		}

		String tempUserDisplayInput = getAndClearTempUserDisplayInput(inputText);
		InputBoxErrorHandler handler = new InputBoxErrorHandler(inputBox, errorHandler,
				tempUserDisplayInput, inputText);

		try {
			updateLinkedGeoNoErrorHandling(inputText, tpl, handler);
		} catch (MyError error) {
			handler.handleError();
			maybeShowError(error);
		} catch (Throwable throwable) {
			handler.handleError();
			Log.error(throwable.getMessage());
			maybeShowError(MyError.Errors.InvalidInput);
		}
	}

	private void updateLinkedGeoNoErrorHandling(String inputText,
												StringTemplate tpl,
												ErrorHandler errorHandler) {
		String defineText = preprocess(inputText, tpl);

		EvalInfo info = new EvalInfo(!kernel.getConstruction().isSuppressLabelsActive(),
				false, false).withSliders(false)
				.withNoRedefinitionAllowed().withPreventingTypeChange()
				.withRedefinitionRule(createRedefinitionRule());

		algebraProcessor.changeGeoElementNoExceptionHandling(linkedGeo,
				defineText, info, false,
				new InputBoxCallback(kernel.getApplication(), this, inputBox), errorHandler);
	}

	private String getAndClearTempUserDisplayInput(String inputText) {
		String tempUserInput = inputBox.getTempUserDisplayInput();
		inputBox.setTempUserDisplayInput(null);
		inputBox.setTempUserEvalInput(null);

		return tempUserInput == null ? inputText : tempUserInput;
	}

	private String preprocess(String inputText, StringTemplate tpl) {
		String defineText = inputText;

		if (linkedGeo.isGeoText()) {
			defineText = "\"" + defineText + "\"";
		} else if ("?".equals(inputText.trim()) || "".equals(inputText.trim())) {
			defineText = "?";
		} else if (linkedGeo.isGeoLine()) {

			// not y=
			// and not Line[A,B]
			if ((defineText.indexOf('=') == -1) && (defineText.indexOf('[') == -1)) {
				// x + 1 changed to
				// y = x + 1
				defineText = "y=" + defineText;
			}

			String prefix = linkedGeo.getLabel(tpl) + ":";
			// need a: in front of
			// X = (-0.69, 0) + \lambda (1, -2)
			if (!defineText.startsWith(prefix)) {
				defineText = prefix + defineText;
			}
		} else if (isComplexNumber()) {

			// make sure user can enter regular "i"
			defineText = defineText.replace('i', Unicode.IMAGINARY);

		}
		if (linkedGeo instanceof FunctionalNVar) {
			// string like f(x,y)=x^2
			// or f(\theta) = \theta
			defineText = linkedGeo.getLabel(tpl) + "("
					+ ((FunctionalNVar) linkedGeo).getVarString(tpl) + ")=" + defineText;
		}
		return defineText;
	}

	private RedefinitionRule createRedefinitionRule() {
		RedefinitionRule same = RedefinitionRules.sameClassRule();
		RedefinitionRule point = RedefinitionRules.oneWayRule(
				GeoClass.POINT3D, GeoClass.POINT);
		RedefinitionRule vector = RedefinitionRules.oneWayRule(
				GeoClass.VECTOR3D, GeoClass.VECTOR);
		return RedefinitionRules.anyRule(same, point, vector);
	}

	boolean isComplexNumber() {
		return linkedGeo.isGeoPoint()
				&& ((GeoPointND) linkedGeo).getToStringMode() == Kernel.COORD_COMPLEX;
	}

	private void maybeShowError(MyError error) {
		if (showErrorDialog) {
			app.showError(error);
		}
	}

	private void maybeShowError(MyError.Errors error) {
		if (showErrorDialog) {
			app.showError(error);
		}
	}
}
