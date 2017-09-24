/*
 *  JavaSMT is an API wrapper for a collection of SMT solvers.
 *  This file is part of JavaSMT.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.sosy_lab.java_smt.solvers.smtinterpol;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import de.uni_freiburg.informatik.ultimate.logic.Annotation;
import de.uni_freiburg.informatik.ultimate.logic.FunctionSymbol;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.sosy_lab.common.collect.Collections3;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverContext.ProverOptions;
import org.sosy_lab.java_smt.api.SolverException;
import org.sosy_lab.java_smt.basicimpl.FormulaCreator;

class SmtInterpolTheoremProver extends SmtInterpolBasicProver<Void, Term>
    implements ProverEnvironment {

  private final SmtInterpolFormulaManager mgr;
  private final SmtInterpolEnvironment env;
  private final Map<String, Term> annotatedTerms; // Collection of termNames
  private final FormulaCreator<Term, Sort, SmtInterpolEnvironment, FunctionSymbol> creator;
  private final boolean generateUnsatCores;

  SmtInterpolTheoremProver(
      SmtInterpolFormulaManager pMgr,
      FormulaCreator<Term, Sort, SmtInterpolEnvironment, FunctionSymbol> pCreator,
      Set<ProverOptions> options) {
    super(pMgr);
    mgr = pMgr;
    env = mgr.createEnvironment();
    creator = pCreator;
    checkNotNull(env);
    annotatedTerms = new HashMap<>();
    generateUnsatCores = options.contains(ProverOptions.GENERATE_UNSAT_CORE);
  }

  @Override
  public Optional<List<BooleanFormula>> unsatCoreOverAssumptions(
      Collection<BooleanFormula> assumptions) throws SolverException, InterruptedException {
    push();
    Preconditions.checkState(
        annotatedTerms.isEmpty(),
        "Empty environment required for UNSAT core" + " over assumptions.");
    for (BooleanFormula assumption : assumptions) {
      String termName = generateTermName();
      Term t = mgr.extractInfo(assumption);
      Term annotated = env.annotate(t, new Annotation(":named", termName));
      annotatedTerms.put(termName, t);
      env.assertTerm(annotated);
    }
    if (!isUnsat()) {
      return Optional.empty();
    }
    List<BooleanFormula> out = getUnsatCore();
    pop();
    return Optional.of(out);
  }

  @Override
  @Nullable
  public Void addConstraint(BooleanFormula constraint) {
    Preconditions.checkState(!isClosed());
    Term t = mgr.extractInfo(constraint);
    if (generateUnsatCores) {
      String termName = generateTermName();
      Term annotated = env.annotate(t, new Annotation(":named", termName));
      annotatedTerms.put(termName, t);
      env.assertTerm(annotated);
    } else {
      env.assertTerm(t);
    }
    assertedFormulas.peek().add(t);
    return null;
  }

  @Override
  public List<BooleanFormula> getUnsatCore() {
    Preconditions.checkState(!isClosed());
    Term[] terms = env.getUnsatCore();
    return Collections3.transformedImmutableListCopy(
        terms, input -> creator.encapsulateBoolean(annotatedTerms.get(input.toString())));
  }

  @Override
  public <T> T allSat(AllSatCallback<T> callback, List<BooleanFormula> important)
      throws InterruptedException, SolverException {
    Preconditions.checkState(!isClosed());
    Term[] importantTerms = new Term[important.size()];
    int i = 0;
    for (BooleanFormula impF : important) {
      importantTerms[i++] = mgr.extractInfo(impF);
    }
    for (Term[] model : env.checkAllSat(importantTerms)) {
      callback.apply(Collections3.transformedImmutableListCopy(model, creator::encapsulateBoolean));
    }
    return callback.getResult();
  }
}
