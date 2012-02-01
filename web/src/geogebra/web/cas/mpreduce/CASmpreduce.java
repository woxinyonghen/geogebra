package geogebra.web.cas.mpreduce;

import java.util.concurrent.TimeoutException;

import geogebra.common.cas.CASException;
import geogebra.common.cas.CASparser;
import geogebra.common.cas.CasExpressionFactory;
import geogebra.common.cas.CasParserTools;
import geogebra.common.cas.mpreduce.AbstractCASmpreduce;
import geogebra.common.kernel.arithmetic.AbstractCommand;
import geogebra.common.kernel.arithmetic.FunctionNVar;
import geogebra.common.kernel.arithmetic.ValidExpression;
import geogebra.common.kernel.arithmetic.ExpressionNodeConstants.StringType;
import geogebra.common.main.AbstractApplication;

public class CASmpreduce extends AbstractCASmpreduce {


	public CASmpreduce(CASparser casParser, CasParserTools t) {
		super(casParser);
		this.parserTools = parserTools;
    }

	@Override
	public String evaluateMPReduce(String exp) {
		try {
			exp = casParser.replaceIndices(exp);
			String ret = evaluateRaw(exp);
			ret = casParser.insertSpecialChars(ret); // undo special character
														// handling

			// convert MPReduce's scientific notation from e.g. 3.24e-4 to
			// 3.2E-4
			ret = parserTools.convertScientificFloatNotation(ret);

			return ret;
		//} catch (TimeoutException toe) {
		//	throw new Error(toe.getMessage());
		} catch (Throwable e) {
			System.err.println("evaluateMPReduce: " + e.getMessage());
			return "?";
		}
	}

	@Override
	public String evaluateGeoGebraCAS(ValidExpression casInput)
	        throws CASException {
		// KeepInput[] command should set flag keepinput!!:=1
		// so that commands like Substitute can work accordingly
		boolean keepInput = casInput.isKeepInputUsed();
		if (keepInput) {
			// remove KeepInput[] command and take argument
			AbstractCommand cmd = casInput.getTopLevelCommand();
			if (cmd != null && cmd.getName().equals("KeepInput")) {
				// use argument of KeepInput as casInput
				if (cmd.getArgumentNumber() > 0)
					casInput = cmd.getArgument(0);
			}
		}

		// convert parsed input to MPReduce string
		String mpreduceInput = translateToCAS(casInput, StringType.MPREDUCE);

		// tell MPReduce whether it should use the keep input flag,
		// e.g. important for Substitute
		StringBuilder sb = new StringBuilder();
		sb.append("<<keepinput!!:=");
		sb.append(keepInput ? 1 : 0);
		sb.append("$ numeric!!:=0$ precision 30$ print\\_precision 16$ off complex, rounded, numval, factor, div, combinelogs, expandlogs, pri$ currentx!!:= ");
		sb.append(casParser.getKernel().getCasVariablePrefix());
		sb.append("x; currenty!!:= ");
		sb.append(casParser.getKernel().getCasVariablePrefix());
		sb.append("y;");
		sb.append(mpreduceInput);
		sb.append(">>");

		// evaluate in MPReduce
		String result = evaluateMPReduce(sb.toString());

		if (keepInput) {
			// when keepinput was treated in MPReduce, it is now > 1
			String keepinputVal = evaluateMPReduce("keepinput!!;");
			boolean keepInputUsed = !"1".equals(keepinputVal);
			if (!keepInputUsed)
				result = casParser.toGeoGebraString(casInput);
		}

		// convert result back into GeoGebra syntax
		if (casInput instanceof FunctionNVar) {
			// function definition f(x) := x^2 should return x^2
			StringType oldPrintForm = casParser.getKernel().getCASPrintForm();
			casParser.getKernel().setCASPrintForm(StringType.GEOGEBRA);
			String ret = casInput.toString();
			casParser.getKernel().setCASPrintForm(oldPrintForm);
			return ret;
		}
		// standard case
		return toGeoGebraString(result);
	}

	@Override
	public String evaluateRaw(String exp) {
		return nativeEvaluateRaw(exp);
	}
	
	public static native String nativeEvaluateRaw(String exp) /*-{
	if (typeof $wnd.callCAS === 'function')
		return $wnd.callCAS(exp);
	}-*/;



	@Override
	public void unbindVariable(String var) {
		AbstractApplication.debug("unimplemented:"+var);
		// TODO Auto-generated method stub

	}

	@Override
	public void reset() {
		AbstractApplication.debug("unimplemented");
		// TODO Auto-generated method stub

	}

	@Override
	public void setSignificantFiguresForNumeric(int significantNumbers) {
		AbstractApplication.debug("unimplemented:"+significantNumbers);
		// TODO Auto-generated method stub

	}
	
	/**
	 * Tries to parse a given MPReduce string and returns a String in GeoGebra
	 * syntax.
	 * 
	 * @param mpreduceString
	 *            String in MPReduce syntax
	 * @return String in Geogebra syntax.
	 * @throws CASException
	 *             Throws if the underlying CAS produces an error
	 */
	public synchronized String toGeoGebraString(String mpreduceString)
			throws CASException {
		ValidExpression ve = casParser.parseMPReduce(mpreduceString);
		
		// replace rational exponents by roots if needed
		if(ve.getKernel().getApplication().getSettings().getCasSettings().getShowExpAsRoots()){
			CasExpressionFactory factory = new CasExpressionFactory(ve);
			factory.replaceExpByRoots();
		}
		return casParser.toGeoGebraString(ve);
	}

	public void initCAS() {
		initMyMPReduceFunctions();
	}
	
	private synchronized void initMyMPReduceFunctions() {

		// user variable ordering
		String variableOrdering = "ggbcasvarx, ggbcasvary, ggbcasvarz, ggbcasvara, "
				+ "ggbcasvarb, ggbcasvarc, ggbcasvard, ggbcasvare, ggbcasvarf, "
				+ "ggbcasvarg, ggbcasvarh, ggbcasvari, ggbcasvarj, ggbcasvark, "
				+ "ggbcasvarl, ggbcasvarm, ggbcasvarn, ggbcasvaro, ggbcasvarp, "
				+ "ggbcasvarq, ggbcasvarr, ggbcasvars, ggbcasvart, ggbcasvaru, "
				+ "ggbcasvarv, ggbcasvarw";
		// make sure to use current kernel's variable prefix
		variableOrdering = variableOrdering.replace("ggbcasvar", casParser
				.getKernel().getCasVariablePrefix());
		if (CASmpreduce.varOrder.length() > 0)
			CASmpreduce.varOrder.append(',');
		CASmpreduce.varOrder.append(variableOrdering);
		evaluateRaw("varorder!!:= list(" + CASmpreduce.varOrder + ");");
		evaluateRaw("order varorder!!;");
		evaluateRaw("korder varorder!!;");

		// access functions for elements of a vector
		String xyzCoordFunctions = "procedure ggbcasvarx(a); first(a);"
				+ "procedure ggbcasvary(a); second(a);"
				+ "procedure ggbcasvarz(a); third(a);";
		// make sure to use current kernel's variable prefix
		xyzCoordFunctions = xyzCoordFunctions.replace("ggbcasvar", casParser
				.getKernel().getCasVariablePrefix());
		evaluateRaw(xyzCoordFunctions);

		// Initialize MPReduce
		evaluateRaw("off nat;");
		evaluateRaw("off pri;");

		evaluateRaw("off numval;");
		evaluateRaw("linelength 50000;");
		evaluateRaw("scientific_notation {16,5};");
		evaluateRaw("on fullroots;");
		evaluateRaw("printprecision!!:=15;");

		evaluateRaw("intrules!!:={"
				+ "int(~w/~x,~x) => w*log(abs(x)) when freeof(w,x),"
				+ "int(~w/(~x+~a),~x) => w*log(abs(x+a)) when freeof(w,x) and freeof(a,x),"
				+ "int((~b*~x+~w)/(~x+~a),~x) => int((b*xw)/(x+a),x)+w*log(abs(x+a)) when freeof(w,x) and freeof(a,x) and freeof(b,x),"
				+ "int((~a*~x+~w)/~x,~x) => int(a,x)+w*log(abs(x)) when freeof(w,x) and freeof(a,x),"
				+ "int((~x+~w)/~x,~x) => x+w*log(abs(x)) when freeof(w,x),"
				+ "int(tan(~x),~x) => log(abs(sec(x))),"
				+ "int(~w*tan(~x),~x) => w*log(abs(sec(x))) when freeof(w,x),"
				+ "int(~w+tan(~x),~x) => int(w,x)+log(abs(sec(x))),"
				+ "int(~a+~w*tan(~x),~x) => int(a,x)+w*log(abs(sec(x))) when freeof(w,x),"
				+ "int(cot(~x),~x) => log(abs(sin(x))),"
				+ "int(~w*cot(~x),~x) => w*log(abs(sin(x))) when freeof(w,x),"
				+ "int(~a+cot(~x),~x) => int(a,x)+log(abs(sin(x))),"
				+ "int(~a+~w*cot(~x),~x) => int(a,x)+w*log(abs(sin(x))) when freeof(w,x),"
				+ "int(sec(~x),~x) => -log(abs(tan(x / 2) - 1)) + log(abs(tan(x / 2) + 1)),"
				+ "int(~w*sec(~x),~x) => -log(abs(tan(x / 2) - 1))*w + log(abs(tan(x / 2) + 1) )*w when freeof(w,x),"
				+ "int(~w+sec(~x),~x) => -log(abs(tan(x / 2) - 1)) + log(abs(tan(x / 2) + 1) )+int(w,x),"
				+ "int(~a+w*sec(~x),~x) => -log(abs(tan(x / 2) - 1))*w + log(abs(tan(x / 2) + 1) )*w+int(a,x) when freeof(w,x),"
				+ "int(csc(~x),~x) => log(abs(tan(x / 2))),"
				+ "int(~w*csc(~x),~x) => w*log(abs(tan(x / 2))) when freeof(w,x),"
				+ "int(~w+csc(~x),~x) => int(w,x)+log(abs(tan(x / 2))),"
				+ "int(~a+~w*csc(~x),~x) => int(a,x)+w*log(abs(tan(x / 2))) when freeof(w,x)"
				+ "};");

		evaluateRaw("let {" + "df(asin(~x),x) => 1/sqrt(1-x^2),"
				+ "df(acosh(~x),x) => 1/(sqrt(x-1)*sqrt(x+1)),"
				+ "df(asinh(~x),x) => 1/sqrt(1+x^2),"
				+ "df(acos(~x),x) => -1/sqrt(1-x^2)};");

		evaluateRaw("let {impart(arbint(~w)) => 0, arbint(~w)*i =>  0};");
		evaluateRaw("let {atan(sin(~x)/cos(~x))=>x, "
				+ "acos(1/sqrt(2)) => pi/4" + "};");

		evaluateRaw("solverules:={" + "logb(~x,~b)=>log(x)/log(b),"
				+ "log10(~x)=>log(x)/log(10)" + "};");

		evaluateRaw("procedure myatan2(y,x);"
				+ " begin scalar xinput, yinput;"
				+ " xinput:=x; yinput:=y;"
				+ " on rounded, roundall, numval;"
				+ " x:=x+0; y:=y+0;"
				+ " return "
				+ " if numberp(y) and numberp(x) then"
				+ "   if x>0 then <<if numeric!!=0 then off rounded, roundall, numval; atan(yinput/xinput)>>"
				+ "   else if x<0 and y>=0 then <<if numeric!!=0 then off rounded, roundall, numval; atan(yinput/xinput)+pi>>"
				+ "   else if x<0 and y<0 then <<if numeric!!=0 then off rounded, roundall, numval; atan(yinput/xinput)-pi>>"
				+ "   else if x=0 and y>0 then <<if numeric!!=0 then off rounded, roundall, numval; pi/2>>"
				+ "   else if x=0 and y<0 then <<if numeric!!=0 then off rounded, roundall, numval; -pi/2>>"
				+ "   else if x=0 and y=0 then <<if numeric!!=0 then off rounded, roundall, numval; 0>>"
				+ "   else '?" + " else" + "   '? end;");

		evaluateRaw("procedure mycoeff(p,x);"
				+ " begin scalar coefflist, bool!!;"
				+ " coefflist:=coeff(p,x);"
				+ " if 1=for each elem!! in coefflist product"
				+ "   if freeof(elem!!,x) then 1 else 0 then"
				+ "   return reverse(coefflist)" + " else" + "   return '?"
				+ " end;");

		evaluateRaw(" Degree := pi/180;");

		evaluateRaw("procedure myround(x);" + "floor(x+0.5);");

		evaluateRaw("procedure harmonic(n,m); for i:=1:n sum 1/(i**m);");
		evaluateRaw("procedure uigamma(n,m); gamma(n)-igamma(n,m);");
		evaluateRaw("procedure beta!Regularized(a,b,x); ibeta(a,b,x);");
		evaluateRaw("procedure myarg(x);"
				+ " if arglength(x)>-1 and part(x,0)='list then myatan2(part(x,2), part(x,1)) "
				+ " else if arglength(x)>-1 and part(x,0)='mat then <<"
				+ "   clear x!!;"
				+ "   x!!:=x;"
				+ "   if row_dim(x!!)=1 then myatan2(x!!(1,2),x!!(1,1))"
				+ "   else if column_dim(x!!)=1 then myatan2(x!!(2,1),x!!(2,1))"
				+ "   else arg(x!!) >>" + " else myatan2(impart(x),repart(x));");
		evaluateRaw("procedure polartocomplex(r,phi); r*(cos(phi)+i*sin(phi));");
		evaluateRaw("procedure polartopoint!\u00a7(r,phi); list(r*cos(phi),r*sin(phi));");
		evaluateRaw("procedure complexexponential(r,phi); r*(cos(phi)+i*sin(phi));");
		evaluateRaw("procedure conjugate(x); conj(x);");
		evaluateRaw("procedure myrandom(); <<on rounded; random(100000001)/(random(100000000)+1)>>;");
		evaluateRaw("procedure gamma!Regularized(a,x); igamma(a,x);");
		evaluateRaw("procedure gamma2(a,x); gamma(a)*igamma(a,x);");
		evaluateRaw("procedure beta3(a,b,x); beta(a,b)*ibeta(a,b,x);");
		evaluateRaw("symbolic procedure isbound!! x; if get(x, 'avalue) then 1 else 0;");
		evaluateRaw("procedure myabs(x);"
				+ " if arglength(x!!)>-1 and part(x,0)='list then sqrt(for each elem!! in x sum elem!!^2)"
				+ " else if arglength(x)>-1 and part(x,0)='mat then <<"
				+ "   clear x!!;"
				+ "   x!!:=x;"
				+ "   if row_dim(x!!)=1 then sqrt(for i:=1:column_dim(x!!) sum x!!(1,i)^2)"
				+ "   else if column_dim(x!!)=1 then sqrt(for i:=1:row_dim(x!!) sum x!!(i,1)^2)"
				+ "   else abs(x!!) >>" + " else if freeof(x,i) then abs(x)"
				+ " else sqrt(repart(x)^2+impart(x)^2);");

		evaluateRaw("procedure flattenlist a;"
				+ "if 1=for each elem!! in a product length(elem!!) then for each elem!! in a join elem!! else a;");

		evaluateRaw("procedure depth a; if arglength(a)>0 and part(a,0)='list then 1+depth(part(a,1)) else 0;");

		evaluateRaw("procedure mysolve(eqn, var);"
				+ " begin scalar solutions!!, bool!!;"
				+ "  eqn:=mkdepthone({eqn});"
				+ "  let solverules;"
				+ "  if arglength(eqn)>-1 and part(eqn,0)='list then"
				+ "    eqn:=for each x in eqn collect"
				+ "      if freeof(x,=) then x else subtraction(lhs(x),rhs(x))"
				+ "  else if freeof(eqn,=) then 1 else eqn:=subtraction(lhs(eqn),rhs(eqn));"
				+ "  solutions!!:=solve(eqn,var);"
				+ "	 if depth(solutions!!)<2 then"
				+ "		solutions!!:=for each x in solutions!! collect {x};"
				+ "	 solutions!!:=for each sol in solutions!! join <<"
				+ "    bool!!:=1;"
				+ "    for each solution!! in sol do"
				+ "      if freeof(solution!!,'root_of) and freeof(solution!!,'one_of) then <<"
				+ "		   on rounded, roundall, numval, complex;"
				+ "		   if freeof(solution!!,'i) or aeval(impart(rhs(solution!!)))=0 then 1 else bool!!:=0;"
				+ "		   off complex;"
				+ "		   if numeric!!=0 then off rounded, roundall, numval"
				+ "      >>" + "      else" + "	       bool!!:=2*bool!!;"
				+ "    if bool!!=1 then" + "  	 {sol}"
				+ "	   else if bool!!>1 then " + "  	 {{var='?}}" + "    else "
				+ "		 {} >>;" + "  clearrules solverules;"
				+ "  return mkset(solutions!!);" + " end;");

		evaluateRaw("procedure mycsolve(eqn, var);"
				+ " begin scalar solutions!!, bool!!;"
				+ "  eqn:=mkdepthone({eqn});"
				+ "  let solverules;"
				+ "  if arglength(eqn)>-1 and part(eqn,0)='list then"
				+ "    eqn:=for each x in eqn collect"
				+ "      if freeof(x,=) then x else subtraction(lhs(x),rhs(x))"
				+ "  else if freeof(eqn,=) then 1 else eqn:=subtraction(lhs(eqn),rhs(eqn));"
				+ "    solutions!!:=solve(eqn,var);"
				+ "    if depth(solutions!!)<2 then"
				+ "      solutions!!:=for each x in solutions!! collect {x};"
				+ "    solutions!!:= for each sol in solutions!! join <<"
				+ "      bool!!:=1;"
				+ "      for each solution!! in sol do"
				+ "        if freeof(solution!!,'root_of) and freeof(solution!!,'one_of) then 1 else"
				+ "      		bool!!:=0;" + "      if bool!!=1 then"
				+ "        {sol}" + "      else if bool!!=0 then"
				+ "        {{var='?}}" + "      >>;"
				+ "  clearrules solverules;" + "  return mkset(solutions!!);"
				+ " end;");

		evaluateRaw("procedure mysolve1(eqn);"
				+ " begin scalar solutions!!, bool!!;"
				+ "  eqn:=mkdepthone({eqn});"
				+ "  let solverules;"
				+ "  if arglength(eqn)>-1 and part(eqn,0)='list then"
				+ "    eqn:=for each x in eqn collect"
				+ "      if freeof(x,=) then x else lhs(x)-rhs(x)"
				+ "  else if freeof(eqn,=) then 1 else eqn:=lhs(eqn)-rhs(eqn);"
				+ "  solutions!!:=solve(eqn);"
				+ "	 if depth(solutions!!)<2 then"
				+ "		solutions!!:=for each x in solutions!! collect {x};"
				+ "	 solutions!!:=for each sol in solutions!! join <<"
				+ "    bool!!:=1;"
				+ "    for each solution!! in sol do"
				+ "      if freeof(solution!!,'root_of) then <<"
				+ "		   on rounded, roundall, numval, complex;"
				+ "		   if freeof(solution!!,'i) or aeval(impart(rhs(solution!!)))=0 then 1 else bool!!:=0;"
				+ "		   off complex;"
				+ "		   if numeric!!=0 then off rounded, roundall, numval"
				+ "      >>" + "      else" + "	       bool!!:=2*bool!!;"
				+ "    if bool!!=1 then" + "  	 {sol}"
				+ "	   else if bool!!>1 then " + "  	 {{'?}}" + "    else "
				+ "		 {} >>;" + "  clearrules solverules;"
				+ "  return mkset(solutions!!);" + " end;");

		evaluateRaw("procedure mycsolve1(eqn);"
				+ " begin scalar solutions!!, bool!!;" + "  let solverules;"
				+ "  eqn:=mkdepthone({eqn});"
				+ "  if arglength(eqn)>-1 and part(eqn,0)='list then"
				+ "    eqn:=for each x in eqn collect"
				+ "      if freeof(x,=) then x else lhs(x)-rhs(x)"
				+ "  else if freeof(eqn,=) then 1 else eqn:=lhs(eqn)-rhs(eqn);"
				+ "    solutions!!:=solve(eqn);"
				+ "    if depth(solutions!!)<2 then"
				+ "      solutions!!:=for each x in solutions!! collect {x};"
				+ "    solutions!!:= for each sol in solutions!! join <<"
				+ "      bool!!:=1;" + "      for each solution!! in sol do"
				+ "        if freeof(solution!!,'root_of) then 1 else"
				+ "      		bool!!:=0;" + "      if bool!!=1 then"
				+ "        {sol}" + "      else if bool!!=0 then"
				+ "        {{var='?}}" + "      >>;"
				+ "  clearrules solverules;" + "  return mkset(solutions!!);"
				+ " end;");

		evaluateRaw("procedure dot(vec1,vec2); "
				+ "	begin scalar tmplength; "
				+ "  if arglength(vec1)>-1 and part(vec1,0)='mat and column_dim(vec1)=1 then "
				+ "    vec1:=tp(vec1);"
				+ "  if arglength(vec2)>-1 and part(vec2,0)='mat and column_dim(vec2)=1 then "
				+ "    vec2:=tp(vec2); "
				+ "  return  "
				+ "  if arglength(vec1)>-1 and part(vec1,0)='list then << "
				+ "    if arglength(vec2)>-1 and part(vec2,0)='list then  "
				+ "      <<tmplength:=length(vec1);  "
				+ "      for i:=1:tmplength  "
				+ "			sum part(vec1,i)*part(vec2,i) >> "
				+ "    else if arglength(vec2)>-1 and part(vec2,0)='mat and row_dim(vec2)=1 then"
				+ "      <<tmplength:=length(vec1);  "
				+ "      for i:=1:tmplength  "
				+ "	sum part(vec1,i)*vec2(1,i)>> "
				+ "      else "
				+ "	'? "
				+ "  >> "
				+ "  else <<if arglength(vec1)>-1 and part(vec1,0)='mat and row_dim(vec1)=1 then << "
				+ "    if arglength(vec2)>-1 and part(vec2,0)='list then  "
				+ "      <<tmplength:=length(vec2); "
				+ "      for i:=1:tmplength  "
				+ "			sum vec1(1,i)*part(vec2,i)>> "
				+ "    else if arglength(vec2)>-1 and part(vec2,0)='mat and row_dim(vec2)=1 then"
				+ "      <<tmplength:=column_dim(vec1);  "
				+ "      for i:=1:tmplength  " + "			sum vec1(1,i)*vec2(1,i) "
				+ "      >> " + "      else " + "		'? " + "    >> " + "  else "
				+ "    '? " + "  >> " + "end;");

		evaluateRaw("procedure cross(atmp,btmp); "
				+ "begin;"
				+ "  a:=atmp; b:= btmp;"
				+ "  if arglength(a)=-1 or (length(a) neq 3 and length(a) neq 2 and length(a) neq {1,3} and length(a) neq {3,1} and length(a) neq {1,2} and length(a) neq {2,1}) then return '?;"
				+ "  if arglength(b)=-1 or (length(b) neq 3 and length(b) neq 2 and length(b) neq {1,3} and length(b) neq {3,1} and length(b) neq {1,2} and length(b) neq {2,1}) then return '?;"
				+ "  if length(a)={1,3} or length(b)={1,2} then a:=tp(a);"
				+ "  if length(b)={1,3} or length(b)={1,2} then b:=tp(b);"
				+ "  return"
				+ "  if arglength(a)>-1 and part(a,0)='mat then <<"
				+ "    if arglength(b)>-1 and part(b,0)='mat then <<"
				+ "      if length(a)={3,1} and length(b)={3,1} then"
				+ "        mat((a(2,1)*b(3,1)-a(3,1)*b(2,1)),"
				+ "        (a(3,1)*b(1,1)-a(1,1)*b(3,1)),"
				+ "        (a(1,1)*b(2,1)-a(2,1)*b(1,1)))"
				+ "      else if length(a)={2,1} and length(b)={2,1} then"
				+ "        mat((0)," + "        (0),"
				+ "        (a(1,1)*b(2,1)-a(2,1)*b(1,1)))" + "      else '?"
				+ "    >> else if arglength(b)>-1 and part(b,0)='list then <<"
				+ "      if length(a)={3,1} and length(b)=3 then"
				+ "        list(a(2,1)*part(b,3)-a(3,1)*part(b,2),"
				+ "        a(3,1)*part(b,1)-a(1,1)*part(b,3),"
				+ "        a(1,1)*part(b,2)-a(2,1)*part(b,1))"
				+ "      else if length(a)={2,1} and length(b)=2 then"
				+ "        list(0," + "        0,"
				+ "        a(1,1)*part(b,2)-a(2,1)*part(b,1))"
				+ "      else '?" + "    >> else << '? >>"
				+ "  >> else if arglength(a)>-1 and part(a,0)='list then <<"
				+ "    if arglength(b)>-1 and part(b,0)='mat then <<"
				+ "      if length(a)=3 and length(b)={3,1} then"
				+ "        list(part(a,2)*b(3,1)-part(a,3)*b(2,1),"
				+ "        part(a,3)*b(1,1)-part(a,1)*b(3,1),"
				+ "        part(a,1)*b(2,1)-part(a,2)*b(1,1))"
				+ "      else if length(a)=2 and length(b)={2,1} then"
				+ "        list(0," + "        0,"
				+ "        part(a,1)*b(2,1)-part(a,2)*b(1,1))"
				+ "      else '?"
				+ "    >> else if arglength(b)>-1 and part(b,0)='list then <<"
				+ "      if length(a)=3 and length(b)=3 then"
				+ "        list(part(a,2)*part(b,3)-part(a,3)*part(b,2),"
				+ "        part(a,3)*part(b,1)-part(a,1)*part(b,3),"
				+ "        part(a,1)*part(b,2)-part(a,2)*part(b,1))"
				+ "      else if length(a)=2 and length(b)=2 then"
				+ "        list(0," + "        0,"
				+ "        part(a,1)*part(b,2)-part(a,2)*part(b,1))"
				+ "      else '?" + "    >> else << '? >>"
				+ "  >> else << '? >> " + "end;");

		evaluateRaw("procedure mattoscalar(m);"
				+ " if length(m)={1,1} then trace(m) else m;");

		evaluateRaw("procedure multiplication(a,b);"
				+ "  if arglength(a)>-1 and part(a,0)='mat then"
				+ "    if arglength(b)>-1 and part(b,0)='mat then"
				+ "      mattoscalar(a*b)"
				+ "    else if arglength(b)>-1 and part(b,0)='list then"
				+ "      mattoscalar(a*<<listtocolumnvector(b)>>)"
				+ "    else"
				+ "      a*b"
				+ "  else if arglength(a)>-1 and part(a,0)='list then"
				+ "    if arglength(b)>-1 and part(b,0)='mat then"
				+ "      mattoscalar(<<listtorowvector(a)>>*b)"
				+ "    else if arglength(b)>-1 and part(b,0)='list then"
				+ "      mattoscalar(<<listtorowvector(a)>>*<<listtocolumnvector(b)>>)"
				+ "    else"
				+ "      map(~w!!*b,a)"
				+ "  else"
				+ "    if arglength(b)>-1 and part(b,0)='list then"
				+ "      map(a*~w!!,b)"
				+ "    else"
				+ "		 if a=infinity then"
				+ "		   if (numberp(b) and b>0) or b=infinity then infinity"
				+ "		   else if (numberp(b) and b<0) or b=-infinity then -infinity"
				+ "		   else '?"
				+ "		 else if a=-infinity then"
				+ "		   if (numberp(b) and b>0) or b=infinity then -infinity"
				+ "		   else if (numberp(b) and b<0) or b=-infinity then infinity"
				+ "		   else '?"
				+ "		 else if b=infinity then"
				+ "		   if (numberp(a) and a>0) or a=infinity then infinity"
				+ "		   else if (numberp(a) and a<0) or a=-infinity then -infinity"
				+ "		   else '?"
				+ "		 else if b=-infinity then"
				+ "		   if (numberp(a) and a>0) or a=infinity then -infinity"
				+ "		   else if (numberp(a) and a<0) or a=infinity then infinity"
				+ "		   else '?" + "		 else" + "        a*b;");

		evaluateRaw("operator multiplication;");

		evaluateRaw("procedure addition(a,b);"
				+ "  if arglength(a)>-1 and part(a,0)='list and arglength(b)>-1 and part(b,0)='list then"
				+ "    for i:=1:length(a) collect part(a,i)+part(b,i)"
				+ "  else if arglength(a)>-1 and part(a,0)='list then"
				+ "    map(~w!!+b,a)"
				+ "  else if arglength(b)>-1 and part(b,0)='list then"
				+ "    map(a+~w!!,b)"
				+ "  else if (a=infinity and b neq -infinity) or (b=infinity and a neq -infinity) then"
				+ "    infinity"
				+ "  else if (a=-infinity and b neq infinity) or (b=-infinity and a neq infinity) then"
				+ "    -infinity" + "  else" + "    a+b;");

		evaluateRaw("operator addition;");

		evaluateRaw("procedure subtraction(a,b);"
				+ "  if arglength(a)>-1 and part(a,0)='list and arglength(b)>-1 and part(b,0)='list then"
				+ "    for i:=1:length(a) collect part(a,i)-part(b,i)"
				+ "  else if arglength(a)<-1 and part(a,0)='list then"
				+ "    map(~w!!-b,a)"
				+ "  else if arglength(b)>-1 and part(b,0)='list then"
				+ "    map(a-~w!!,b)"
				+ "  else if (a=infinity and b neq infinity) or (b=-infinity and a neq -infinity) then "
				+ "    infinity"
				+ "  else if (a=-infinity and b neq -infinity) or (b=infinity and a neq infinity) then "
				+ "    -infinity" + "  else" + "    a-b;");

		evaluateRaw("operator subtraction;");

		// erf in Reduce is currently broken:
		// http://sourceforge.net/projects/reduce-algebra/forums/forum/899364/topic/4546339
		// this is a numeric approximation according to Abramowitz & Stegun
		// 7.1.26.
		evaluateRaw("procedure myerf(x); "
				+ "begin scalar a1!!, a2!!, a3!!, a4!!, a5!!, p!!, x!!, t!!, y!!, sign!!, result!!;"
				+ "     on rounded;"
				+ "		if numberp(x) then 1 else return !*hold(erf(x));"
				+ "     if x=0 then return 0;"
				+ "     a1!! :=  0.254829592; "
				+ "     a2!! := -0.284496736; "
				+ "     a3!! :=  1.421413741; "
				+ "     a4!! := -1.453152027; "
				+ "     a5!! :=  1.061405429; "
				+ "     p!!  :=  0.3275911; "
				+ "     sign!! := 1; "
				+ "     if x < 0 then sign!! := -1; "
				+ "     x!! := Abs(x); "
				+ "     t!! := 1.0/(1.0 + p!!*x!!); "
				+ "     y!! := 1.0 - (((((a5!!*t!! + a4!!)*t!!) + a3!!)*t!! + a2!!)*t!! + a1!!)*t!!*Exp(-x!!*x!!); "
				+ "     result!! := sign!!*y!!;"
				+ "     if numeric!!=1 then off rounded;"
				+ "     return result!! " + "end;");

		evaluateRaw("procedure mkdepthone(liste);"
				+ "	for each x in liste join "
				+ "	if arglength(x)>-1 and part(x,0)='list then"
				+ "	mkdepthone(x) else {x};");

		evaluateRaw("procedure listtocolumnvector(list); "
				+ "begin scalar lengthoflist; "
				+ "lengthoflist:=length(list); "
				+ "matrix m!!(lengthoflist,1); " + "for i:=1:lengthoflist do "
				+ "m!!(i,1):=part(list,i); " + "return m!! " + "end;");

		evaluateRaw("procedure listtorowvector(list); "
				+ "begin scalar lengthoflist; "
				+ "	lengthoflist:=length(list); "
				+ "	matrix m!!(1,lengthoflist); "
				+ "	for i:=1:lengthoflist do " + "		m!!(1,i):=part(list,i); "
				+ "	return m!!; " + "end;");

		evaluateRaw("procedure mod!!(a,b);" + " a-b*div(a,b);");

		evaluateRaw("procedure div(a,b);"
				+ " begin scalar a!!, b!!, result!!;" + "  a!!:=a; b!!:=b;"
				+ "  on rounded, roundall, numval;" + "  return "
				+ "  if numberp(a!!) and numberp(b!!) then <<"
				+ "    if numeric!!=0 then"
				+ "      off rounded, roundall, numval;" + "    if b!!>0 then "
				+ "	   floor(a/b)" + "    else" + "      ceiling(a/b)"
				+ "  >> else << " + "    if numeric!!=0 then"
				+ "      off rounded, roundall, numval;" + "    on rational;"
				+ "    result!!:=part(divide(a,b),1);" + "    off rational;"
				+ "    if numeric!!=1 then on rounded, roundall, numval;"
				+ "    result!!>>" + " end;");

		// to avoid using the package assist
		evaluateRaw("procedure mkset a;" + " begin scalar result, bool;"
				+ "  result:=list();" + "  for each elem in a do <<"
				+ "  bool:=1;" + "  for each x in result do"
				+ "    if elem=x then bool:=0;" + "  if bool=1 then"
				+ "    result:=elem . result;" + "  >>;"
				+ "  return reverse(result)" + " end;");

		evaluateRaw("procedure shuffle a;"
				+ "begin scalar lengtha,s,tmp;" + " lengtha:=length(a);"
				+ " if lengtha>1 then"
				+ "  for i:=lengtha step -1 until 1 do <<"
				+ "   s:=random(i)+1;" + "   tmp:= part(a,i);"
				+ "   a:=(part(a,i):=part(a,s));" + "   a:=(part(a,s):=tmp);"
				+ "  >>;" + " return a " + "end;");

		evaluateRaw("procedure listofliststomat(a); "
				+ " begin scalar length!!, bool!!, i!!, elem!!;"
				+ "  return"
				+ "  if arglength(a)>-1 and part(a,0)='list then <<"
				+ "    length!!:=-1;"
				+ "    bool!!:=1;"
				+ "    i!!:=0;"
				+ "    while i!!<length(a) and bool!!=1 do <<"
				+ "      i!!:=i!!+1;"
				+ "      elem!!:=part(a,i!!);"
				+ "      if arglength(elem!!)<0 or part(elem!!,0) neq 'list or (length(elem!!) neq length!! and length!! neq -1) then"
				+ "        bool!!:=0"
				+ "      else <<"
				+ "        length!!:=length(elem!!);"
				+ "        if 0=(for i:=1:length(elem!!) product if freeof(elem!!,=) then 1 else 0) then"
				+ "          bool!!:=0;" + "      >>" + "    >>;"
				+ "    if bool!!=0 or length(a)=0 then a" + "    else <<"
				+ "      matrix matrix!!(length(a),length(part(a,1)));"
				+ "      for i:=1:length(a) do"
				+ "        for j!!:=1:length(part(a,1)) do"
				+ "          matrix!!(i,j!!):=part(part(a,i),j!!);"
				+ "      matrix!!>>" + "    >>" + " else" + "    a;" + " end;");

		evaluateRaw("procedure mattolistoflists(a);"
				+ " begin scalar list!!, j!!;" + "  tmpmatrix!!:=a;"
				+ "  return" + "  if arglength(a)<0 or part(a,0) neq 'mat then"
				+ "    tmpmatrix" + "  else"
				+ "    for i:=1:part(length(a),1) collect"
				+ "      for j!!:=1:part(length(a),2) collect"
				+ "        tmpmatrix!!(i,j!!)" + " end;");

		evaluateRaw("procedure mysort a;"
				+ "begin scalar leftlist, rightlist, eqlist;"
				+ " leftlist:=list();"
				+ " rightlist:=list();"
				+ " eqlist:=list();"
				+ " return"
				+ " if length(a)<2 then a"
				+ " else <<"
				+ "  for each elem in a do"
				+ "    if elem<part(a,1) then"
				+ "     leftlist:=elem . leftlist"
				+ "    else if elem=part(a,1) then"
				+ "     eqlist:=elem . eqlist"
				+ "    else"
				+ "     rightlist:=elem . rightlist;"
				+ "  if length(leftlist)=0 and length(rightlist)=0 then"
				+ "    eqlist"
				+ "  else if length(leftlist)=0 then"
				+ "    append(eqlist, mysort(rightlist))"
				+ "  else if length(rightlist)=0 then"
				+ "    append(mysort(leftlist), eqlist)"
				+ "  else"
				+ "    append(append(mysort(leftlist),eqlist),mysort(rightlist))"
				+ " >> " + "end;");

		evaluateRaw("procedure getkernels(a);"
				+ "	for each element in a sum"
				+ "	  if arglength(element)=-1 then" + "	    element"
				+ "	  else" + "	    getkernels(part(element,0):=list);");

		evaluateRaw("procedure mymainvaraux a;"
				+ "if numberp(a) then currentx!! else a;");

		evaluateRaw("procedure mymainvar a;"
				+ "mainvar(mymainvaraux(getkernels(list(a))));");

		evaluateRaw("procedure myint(exp!!, var!!, from!!, to!!);"
				+ "begin scalar integrand!!;"
				+ "antiderivative!!:=int(exp!!, var!!);"
				+ "return sub(var!!=to!!,antiderivative!!)-sub(var!!=from!!,antiderivative!!)"
				+ "end;");
	}



}
