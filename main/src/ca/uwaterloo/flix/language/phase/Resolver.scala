/*
 *  Copyright 2017 Magnus Madsen
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package ca.uwaterloo.flix.language.phase

import ca.uwaterloo.flix.api.Flix
import ca.uwaterloo.flix.language.GenSym
import ca.uwaterloo.flix.language.ast._
import ca.uwaterloo.flix.language.errors.ResolutionError
import ca.uwaterloo.flix.util.Validation._
import ca.uwaterloo.flix.util.{InternalCompilerException, Validation}

import scala.collection.mutable

/**
  * The Resolver phase performs name resolution on the program.
  */
object Resolver extends Phase[NamedAst.Program, ResolvedAst.Program] {

  /**
    * Performs name resolution on the given program `prog0`.
    */
  def run(prog0: NamedAst.Program)(implicit flix: Flix): Validation[ResolvedAst.Program, ResolutionError] = {

    implicit val _ = flix.genSym

    val b = System.nanoTime()

    val definitionsVal = prog0.defs.flatMap {
      case (ns0, defs) => defs.map {
        case (_, defn) => resolve(defn, ns0, prog0) map {
          case d => d.sym -> d
        }
      }
    }

    val namedVal = prog0.named.map {
      case (sym, exp0) => Expressions.resolve(exp0, Name.RootNS, prog0).map {
        case exp =>
          // Introduce a synthetic definition for the expression.
          val doc = None
          val ann = Ast.Annotations.Empty
          val mod = Ast.Modifiers.Empty
          val tparams = Nil
          val fparams = Nil
          val sc = Scheme(Nil, Type.freshTypeVar())
          val eff = Eff.Pure
          val loc = SourceLocation.Unknown
          val defn = ResolvedAst.Def(doc, ann, mod, sym, tparams, fparams, exp, sc, eff, loc)
          sym -> defn
      }
    }

    val enumsVal = prog0.enums.flatMap {
      case (ns0, enums) => enums.map {
        case (_, enum) => resolve(enum, ns0, prog0) map {
          case d => d.sym -> d
        }
      }
    }

    val latticesVal = prog0.lattices.map {
      case (tpe0, lattice0) =>
        for {
          tpe <- lookupType(tpe0, lattice0.ns, prog0)
          lattice <- resolve(lattice0, lattice0.ns, prog0)
        } yield (tpe, lattice)
    }

    val indexesVal = prog0.indexes.flatMap {
      case (ns0, indexes) => indexes.map {
        case (_, index) => resolve(index, ns0, prog0) map {
          case i => i.sym -> i
        }
      }
    }

    val tablesVal = prog0.tables.flatMap {
      case (ns0, tables) => tables.map {
        case (_, table) => Tables.resolve(table, ns0, prog0) map {
          case t => t.sym -> t
        }
      }
    }

    val constraintsVal = prog0.constraints.map {
      case (ns0, constraints) => Constraints.resolve(constraints, ns0, prog0)
    }

    val propertiesVal = prog0.properties.map {
      case (ns0, properties) => Properties.resolve(properties, ns0, prog0)
    }

    val e = System.nanoTime() - b

    for {
      definitions <- seqM(definitionsVal)
      named <- seqM(namedVal)
      enums <- seqM(enumsVal)
      lattices <- seqM(latticesVal)
      indexes <- seqM(indexesVal)
      tables <- seqM(tablesVal)
      constraints <- seqM(constraintsVal)
      properties <- seqM(propertiesVal)
    } yield ResolvedAst.Program(definitions.toMap ++ named.toMap, enums.toMap, lattices.toMap, indexes.toMap, tables.toMap, constraints.flatten, prog0.hooks, properties.flatten, prog0.reachable, prog0.time.copy(resolver = e))

  }

  object Constraints {

    /**
      * Performs name resolution on the given `constraints` in the given namespace `ns0`.
      */
    def resolve(constraints: List[NamedAst.Constraint], ns0: Name.NName, prog0: NamedAst.Program)(implicit genSym: GenSym): Validation[List[ResolvedAst.Constraint], ResolutionError] = {
      seqM(constraints.map(c => resolve(c, ns0, prog0)))
    }

    /**
      * Performs name resolution on the given constraint `c0` in the given namespace `ns0`.
      */
    def resolve(c0: NamedAst.Constraint, ns0: Name.NName, prog0: NamedAst.Program)(implicit genSym: GenSym): Validation[ResolvedAst.Constraint, ResolutionError] = {
      for {
        ps <- seqM(c0.cparams.map(p => Params.resolve(p, ns0, prog0)))
        h <- Predicates.Head.resolve(c0.head, ns0, prog0)
        bs <- seqM(c0.body.map(b => Predicates.Body.resolve(b, ns0, prog0)))
      } yield ResolvedAst.Constraint(ps, h, bs, c0.loc)
    }

  }

  /**
    * Performs name resolution on the given definition `d0` in the given namespace `ns0`.
    */
  def resolve(d0: NamedAst.Def, ns0: Name.NName, prog0: NamedAst.Program)(implicit genSym: GenSym): Validation[ResolvedAst.Def, ResolutionError] = {
    val schemeVal = for {
      base <- lookupType(d0.sc.base, ns0, prog0)
    } yield Scheme(d0.sc.quantifiers, base)

    for {
      tparams <- seqM(d0.tparams.map(tparam => Params.resolve(tparam, ns0, prog0)))
      fparams <- seqM(d0.fparams.map(fparam => Params.resolve(fparam, ns0, prog0)))
      e <- Expressions.resolve(d0.exp, ns0, prog0)
      sc <- schemeVal
    } yield ResolvedAst.Def(d0.doc, d0.ann, d0.mod, d0.sym, tparams, fparams, e, sc, d0.eff, d0.loc)

  }

  /**
    * Performs name resolution on the given enum `e0` in the given namespace `ns0`.
    */
  def resolve(e0: NamedAst.Enum, ns0: Name.NName, prog0: NamedAst.Program)(implicit genSym: GenSym): Validation[ResolvedAst.Enum, ResolutionError] = {
    val casesVal = e0.cases.map {
      case (name, NamedAst.Case(enum, tag, tpe)) =>
        for {
          t <- lookupType(tpe, ns0, prog0)
        } yield name -> ResolvedAst.Case(enum, tag, t)
    }

    for {
      tparams <- seqM(e0.tparams.map(p => Params.resolve(p, ns0, prog0)))
      cases <- seqM(casesVal)
      tpe <- lookupType(e0.tpe, ns0, prog0)
    } yield ResolvedAst.Enum(e0.doc, e0.mod, e0.sym, tparams, cases.toMap, tpe, e0.loc)
  }

  /**
    * Performs name resolution on the given index `i0` in the given namespace `ns0`.
    */
  def resolve(i0: NamedAst.Index, ns0: Name.NName, prog0: NamedAst.Program)(implicit genSym: GenSym): Validation[ResolvedAst.Index, ResolutionError] = {
    for {
      d <- lookupTable(i0.qname, ns0, prog0)
    } yield ResolvedAst.Index(d.sym, i0.indexes, i0.loc)
  }

  /**
    * Performs name resolution on the given lattice `l0` in the given namespace `ns0`.
    */
  def resolve(l0: NamedAst.Lattice, ns0: Name.NName, prog0: NamedAst.Program)(implicit genSym: GenSym): Validation[ResolvedAst.Lattice, ResolutionError] = {
    for {
      tpe <- lookupType(l0.tpe, ns0, prog0)
      bot <- Expressions.resolve(l0.bot, ns0, prog0)
      top <- Expressions.resolve(l0.top, ns0, prog0)
      equ <- Expressions.resolve(l0.equ, ns0, prog0)
      leq <- Expressions.resolve(l0.leq, ns0, prog0)
      lub <- Expressions.resolve(l0.lub, ns0, prog0)
      glb <- Expressions.resolve(l0.glb, ns0, prog0)
    } yield ResolvedAst.Lattice(tpe, bot, top, equ, leq, lub, glb, ns0, l0.loc)
  }

  object Tables {

    /**
      * Performs name resolution on the given table `t0` in the given namespace `ns0`.
      */
    def resolve(t0: NamedAst.Table, ns0: Name.NName, prog0: NamedAst.Program)(implicit genSym: GenSym): Validation[ResolvedAst.Table, ResolutionError] = t0 match {
      case NamedAst.Table.Relation(doc, sym, attr, loc) =>
        for {
          as <- seqM(attr.map(a => resolve(a, ns0, prog0)))
        } yield ResolvedAst.Table.Relation(doc, sym, as, loc)

      case NamedAst.Table.Lattice(doc, sym, keys, value, loc) =>
        for {
          ks <- seqM(keys.map(k => resolve(k, ns0, prog0)))
          v <- resolve(value, ns0, prog0)
        } yield ResolvedAst.Table.Lattice(doc, sym, ks, v, loc)
    }

    /**
      * Performs name resolution on the given attribute `a0` in the given namespace `ns0`.
      */
    private def resolve(a0: NamedAst.Attribute, ns0: Name.NName, prog0: NamedAst.Program)(implicit genSym: GenSym): Validation[ResolvedAst.Attribute, ResolutionError] = {
      for {
        tpe <- lookupType(a0.tpe, ns0, prog0)
      } yield ResolvedAst.Attribute(a0.ident, tpe, a0.loc)
    }

  }

  object Expressions {

    /**
      * Performs name resolution on the given expression `exp0` in the namespace `ns0`.
      */
    def resolve(exp0: NamedAst.Expression, ns0: Name.NName, prog0: NamedAst.Program)(implicit genSym: GenSym): Validation[ResolvedAst.Expression, ResolutionError] = {
      /**
        * Local visitor.
        */
      def visit(e0: NamedAst.Expression): Validation[ResolvedAst.Expression, ResolutionError] = e0 match {
        case NamedAst.Expression.Wild(tpe, loc) => ResolvedAst.Expression.Wild(tpe, loc).toSuccess

        case NamedAst.Expression.Var(sym, loc) => ResolvedAst.Expression.Var(sym, loc).toSuccess

        case NamedAst.Expression.Def(ref, tvar, loc) =>
          lookupDef(ref, ns0, prog0) map {
            case DefTarget.Defn(defn) =>
              ResolvedAst.Expression.Def(defn.sym, tvar, loc)
            case DefTarget.Hook(hook) =>
              ResolvedAst.Expression.Hook(hook, hook.tpe, loc)
          }

        case NamedAst.Expression.Hole(name, tpe, loc) =>
          val sym = Symbol.mkHoleSym(ns0, name)
          ResolvedAst.Expression.Hole(sym, tpe, loc).toSuccess

        case NamedAst.Expression.Unit(loc) => ResolvedAst.Expression.Unit(loc).toSuccess

        case NamedAst.Expression.True(loc) => ResolvedAst.Expression.True(loc).toSuccess

        case NamedAst.Expression.False(loc) => ResolvedAst.Expression.False(loc).toSuccess

        case NamedAst.Expression.Char(lit, loc) => ResolvedAst.Expression.Char(lit, loc).toSuccess

        case NamedAst.Expression.Float32(lit, loc) => ResolvedAst.Expression.Float32(lit, loc).toSuccess

        case NamedAst.Expression.Float64(lit, loc) => ResolvedAst.Expression.Float64(lit, loc).toSuccess

        case NamedAst.Expression.Int8(lit, loc) => ResolvedAst.Expression.Int8(lit, loc).toSuccess

        case NamedAst.Expression.Int16(lit, loc) => ResolvedAst.Expression.Int16(lit, loc).toSuccess

        case NamedAst.Expression.Int32(lit, loc) => ResolvedAst.Expression.Int32(lit, loc).toSuccess

        case NamedAst.Expression.Int64(lit, loc) => ResolvedAst.Expression.Int64(lit, loc).toSuccess

        case NamedAst.Expression.BigInt(lit, loc) => ResolvedAst.Expression.BigInt(lit, loc).toSuccess

        case NamedAst.Expression.Str(lit, loc) => ResolvedAst.Expression.Str(lit, loc).toSuccess

        case NamedAst.Expression.Apply(lambda, args, tvar, loc) =>
          for {
            e <- visit(lambda)
            es <- seqM(args map visit)
          } yield ResolvedAst.Expression.Apply(e, es, tvar, loc)

        case NamedAst.Expression.Lambda(fparams, exp, tvar, loc) =>
          for {
            e <- visit(exp)
            fs <- seqM(fparams.map(fparam => Params.resolve(fparam, ns0, prog0)))
          } yield ResolvedAst.Expression.Lambda(fs, e, tvar, loc)

        case NamedAst.Expression.Unary(op, exp, tvar, loc) =>
          for {
            e <- visit(exp)
          } yield ResolvedAst.Expression.Unary(op, e, tvar, loc)

        case NamedAst.Expression.Binary(op, exp1, exp2, tvar, loc) =>
          for {
            e1 <- visit(exp1)
            e2 <- visit(exp2)
          } yield ResolvedAst.Expression.Binary(op, e1, e2, tvar, loc)

        case NamedAst.Expression.IfThenElse(exp1, exp2, exp3, tvar, loc) =>
          for {
            e1 <- visit(exp1)
            e2 <- visit(exp2)
            e3 <- visit(exp3)
          } yield ResolvedAst.Expression.IfThenElse(e1, e2, e3, tvar, loc)

        case NamedAst.Expression.Let(sym, exp1, exp2, tvar, loc) =>
          for {
            e1 <- visit(exp1)
            e2 <- visit(exp2)
          } yield ResolvedAst.Expression.Let(sym, e1, e2, tvar, loc)

        case NamedAst.Expression.LetRec(sym, exp1, exp2, tvar, loc) =>
          for {
            e1 <- visit(exp1)
            e2 <- visit(exp2)
          } yield ResolvedAst.Expression.LetRec(sym, e1, e2, tvar, loc)

        case NamedAst.Expression.Match(exp, rules, tvar, loc) =>
          val rulesVal = rules map {
            case NamedAst.MatchRule(pat, guard, body) =>
              for {
                p <- Patterns.resolve(pat, ns0, prog0)
                g <- visit(guard)
                b <- visit(body)
              } yield ResolvedAst.MatchRule(p, g, b)
          }

          for {
            e <- visit(exp)
            rs <- seqM(rulesVal)
          } yield ResolvedAst.Expression.Match(e, rs, tvar, loc)

        case NamedAst.Expression.Switch(rules, tvar, loc) =>
          val rulesVal = rules map {
            case (cond, body) => @@(visit(cond), visit(body))
          }
          seqM(rulesVal) map {
            case rs => ResolvedAst.Expression.Switch(rs, tvar, loc)
          }

        case NamedAst.Expression.Tag(enum, tag, expOpt, tvar, loc) => expOpt match {
          case None =>
            // Case 1: The tag has does not have an expression.
            // Either it is implicitly Unit or the tag is used as a function.

            // Lookup the enum to determine the type of the tag.
            lookupEnumByTag(enum, tag, ns0, prog0) map {
              case decl =>
                // Retrieve the relevant case.
                val caze = decl.cases(tag.name)

                // Check if the tag value has Unit type.
                if (isUnitType(caze.tpe)) {
                  // Case 1.1: The tag value has Unit type. Construct the Unit expression.
                  val e = ResolvedAst.Expression.Unit(loc)
                  ResolvedAst.Expression.Tag(decl.sym, tag.name, e, tvar, loc)
                } else {
                  // Case 1.2: The tag has a non-Unit type. Hence the tag is used as a function.
                  // If the tag is `Some` we construct the lambda: x -> Some(x).

                  // Construct a fresh symbol for the formal parameter.
                  val freshVar = Symbol.freshVarSym("x")

                  // Construct the formal parameter for the fresh symbol.
                  val freshParam = ResolvedAst.FormalParam(freshVar, Ast.Modifiers.Empty, Type.freshTypeVar(), loc)

                  // Construct a variable expression for the fresh symbol.
                  val varExp = ResolvedAst.Expression.Var(freshVar, loc)

                  // Construct the tag expression on the fresh symbol expression.
                  val tagExp = ResolvedAst.Expression.Tag(decl.sym, caze.tag.name, varExp, Type.freshTypeVar(), loc)

                  // Assemble the lambda expressions.
                  ResolvedAst.Expression.Lambda(List(freshParam), tagExp, Type.freshTypeVar(), loc)
                }
            }
          case Some(exp) =>
            // Case 2: The tag has an expression. Perform resolution on it.
            for {
              d <- lookupEnumByTag(enum, tag, ns0, prog0)
              e <- visit(exp)
            } yield ResolvedAst.Expression.Tag(d.sym, tag.name, e, tvar, loc)
        }

        case NamedAst.Expression.Tuple(elms, tvar, loc) =>
          for {
            es <- seqM(elms map visit)
          } yield ResolvedAst.Expression.Tuple(es, tvar, loc)

        case NamedAst.Expression.ArrayNew(elm, len, tvar, loc) =>
          for {
            e <- visit(elm)
          } yield ResolvedAst.Expression.ArrayNew(e, len, tvar, loc)

        case NamedAst.Expression.ArrayLit(elms, tvar, loc) =>
          for {
            es <- seqM(elms map visit)
          } yield ResolvedAst.Expression.ArrayLit(es, tvar, loc)

        case NamedAst.Expression.ArrayLoad(base, index, tvar, loc) =>
          for {
            b <- visit(base)
            i <- visit(index)
          } yield ResolvedAst.Expression.ArrayLoad(b, i, tvar, loc)

        case NamedAst.Expression.ArrayStore(base, index, value, tvar, loc) =>
          for {
            b <- visit(base)
            i <- visit(index)
            v <- visit(value)
          } yield ResolvedAst.Expression.ArrayStore(b, i, v, tvar, loc)

        case NamedAst.Expression.Ref(exp, tvar, loc) =>
          for {
            e <- visit(exp)
          } yield ResolvedAst.Expression.Ref(e, tvar, loc)

        case NamedAst.Expression.Deref(exp, tvar, loc) =>
          for {
            e <- visit(exp)
          } yield ResolvedAst.Expression.Deref(e, tvar, loc)

        case NamedAst.Expression.Assign(exp1, exp2, tvar, loc) =>
          for {
            e1 <- visit(exp1)
            e2 <- visit(exp2)
          } yield ResolvedAst.Expression.Assign(e1, e2, tvar, loc)

        case NamedAst.Expression.Existential(fparam, exp, loc) =>
          for {
            fp <- Params.resolve(fparam, ns0, prog0)
            e <- visit(exp)
          } yield ResolvedAst.Expression.Existential(fp, e, loc)

        case NamedAst.Expression.Universal(fparam, exp, loc) =>
          for {
            fp <- Params.resolve(fparam, ns0, prog0)
            e <- visit(exp)
          } yield ResolvedAst.Expression.Universal(fp, e, loc)

        case NamedAst.Expression.Ascribe(exp, tpe, eff, loc) =>
          for {
            e <- visit(exp)
            t <- lookupType(tpe, ns0, prog0)
          } yield ResolvedAst.Expression.Ascribe(e, t, eff, loc)

        case NamedAst.Expression.Cast(exp, tpe, eff, loc) =>
          for {
            e <- visit(exp)
            t <- lookupType(tpe, ns0, prog0)
          } yield ResolvedAst.Expression.Cast(e, t, eff, loc)

        case NamedAst.Expression.NativeConstructor(constructor, args, tpe, loc) =>
          for {
            es <- seqM(args map visit)
          } yield ResolvedAst.Expression.NativeConstructor(constructor, es, tpe, loc)

        case NamedAst.Expression.NativeField(field, tpe, loc) => ResolvedAst.Expression.NativeField(field, tpe, loc).toSuccess

        case NamedAst.Expression.NativeMethod(method, args, tpe, loc) =>
          for {
            es <- seqM(args map visit)
          } yield ResolvedAst.Expression.NativeMethod(method, es, tpe, loc)

        case NamedAst.Expression.UserError(tvar, loc) => ResolvedAst.Expression.UserError(tvar, loc).toSuccess
      }

      visit(exp0)
    }

  }

  object Patterns {

    /**
      * Performs name resolution on the given pattern `pat0` in the namespace `ns0`.
      */
    def resolve(pat0: NamedAst.Pattern, ns0: Name.NName, prog0: NamedAst.Program): Validation[ResolvedAst.Pattern, ResolutionError] = {

      def visit(p0: NamedAst.Pattern): Validation[ResolvedAst.Pattern, ResolutionError] = p0 match {
        case NamedAst.Pattern.Wild(tvar, loc) => ResolvedAst.Pattern.Wild(tvar, loc).toSuccess

        case NamedAst.Pattern.Var(sym, tvar, loc) => ResolvedAst.Pattern.Var(sym, tvar, loc).toSuccess

        case NamedAst.Pattern.Unit(loc) => ResolvedAst.Pattern.Unit(loc).toSuccess

        case NamedAst.Pattern.True(loc) => ResolvedAst.Pattern.True(loc).toSuccess

        case NamedAst.Pattern.False(loc) => ResolvedAst.Pattern.False(loc).toSuccess

        case NamedAst.Pattern.Char(lit, loc) => ResolvedAst.Pattern.Char(lit, loc).toSuccess

        case NamedAst.Pattern.Float32(lit, loc) => ResolvedAst.Pattern.Float32(lit, loc).toSuccess

        case NamedAst.Pattern.Float64(lit, loc) => ResolvedAst.Pattern.Float64(lit, loc).toSuccess

        case NamedAst.Pattern.Int8(lit, loc) => ResolvedAst.Pattern.Int8(lit, loc).toSuccess

        case NamedAst.Pattern.Int16(lit, loc) => ResolvedAst.Pattern.Int16(lit, loc).toSuccess

        case NamedAst.Pattern.Int32(lit, loc) => ResolvedAst.Pattern.Int32(lit, loc).toSuccess

        case NamedAst.Pattern.Int64(lit, loc) => ResolvedAst.Pattern.Int64(lit, loc).toSuccess

        case NamedAst.Pattern.BigInt(lit, loc) => ResolvedAst.Pattern.BigInt(lit, loc).toSuccess

        case NamedAst.Pattern.Str(lit, loc) => ResolvedAst.Pattern.Str(lit, loc).toSuccess

        case NamedAst.Pattern.Tag(enum, tag, pat, tvar, loc) =>
          for {
            d <- lookupEnumByTag(enum, tag, ns0, prog0)
            p <- visit(pat)
          } yield ResolvedAst.Pattern.Tag(d.sym, tag.name, p, tvar, loc)

        case NamedAst.Pattern.Tuple(elms, tvar, loc) =>
          for {
            es <- seqM(elms map visit)
          } yield ResolvedAst.Pattern.Tuple(es, tvar, loc)
      }

      visit(pat0)
    }

  }

  object Predicates {

    object Head {
      /**
        * Performs name resolution on the given head predicate `h0` in the given namespace `ns0`.
        */
      def resolve(h0: NamedAst.Predicate.Head, ns0: Name.NName, prog0: NamedAst.Program)(implicit genSym: GenSym): Validation[ResolvedAst.Predicate.Head, ResolutionError] = h0 match {
        case NamedAst.Predicate.Head.True(loc) => ResolvedAst.Predicate.Head.True(loc).toSuccess

        case NamedAst.Predicate.Head.False(loc) => ResolvedAst.Predicate.Head.False(loc).toSuccess

        case NamedAst.Predicate.Head.Atom(qname, terms, loc) =>
          for {
            t <- lookupTable(qname, ns0, prog0)
            ts <- seqM(terms.map(t => Expressions.resolve(t, ns0, prog0)))
          } yield ResolvedAst.Predicate.Head.Atom(t.sym, ts, loc)
      }
    }

    object Body {
      /**
        * Performs name resolution on the given body predicate `b0` in the given namespace `ns0`.
        */
      def resolve(b0: NamedAst.Predicate.Body, ns0: Name.NName, prog0: NamedAst.Program)(implicit genSym: GenSym): Validation[ResolvedAst.Predicate.Body, ResolutionError] = b0 match {
        case NamedAst.Predicate.Body.Atom(qname, polarity, terms, loc) =>
          for {
            d <- lookupTable(qname, ns0, prog0)
            ts <- seqM(terms.map(t => Patterns.resolve(t, ns0, prog0)))
          } yield ResolvedAst.Predicate.Body.Atom(d.sym, polarity, ts, loc)

        case NamedAst.Predicate.Body.Filter(qname, terms, loc) =>
          lookupDef(qname, ns0, prog0) flatMap {
            case DefTarget.Defn(defn) =>
              for {
                ts <- seqM(terms.map(t => Expressions.resolve(t, ns0, prog0)))
              } yield ResolvedAst.Predicate.Body.Filter(defn.sym, ts, loc)
            case DefTarget.Hook(hook) => throw InternalCompilerException(s"Hook not allowed here: ${loc.format}")
          }

        case NamedAst.Predicate.Body.Loop(pat, term, loc) =>
          for {
            p <- Patterns.resolve(pat, ns0, prog0)
            t <- Expressions.resolve(term, ns0, prog0)
          } yield ResolvedAst.Predicate.Body.Loop(p, t, loc)
      }
    }

  }

  object Properties {

    /**
      * Performs name resolution on each of the given `properties` in the given namespace `ns0`.
      */
    def resolve(properties: List[NamedAst.Property], ns0: Name.NName, prog0: NamedAst.Program)(implicit genSym: GenSym): Validation[List[ResolvedAst.Property], ResolutionError] = {
      seqM(properties.map(p => resolve(p, ns0, prog0)))
    }

    /**
      * Performs name resolution on the given property `p0` in the given namespace `ns0`.
      */
    def resolve(p0: NamedAst.Property, ns0: Name.NName, prog0: NamedAst.Program)(implicit genSym: GenSym): Validation[ResolvedAst.Property, ResolutionError] = {
      for {
        e <- Expressions.resolve(p0.exp, ns0, prog0)
      } yield ResolvedAst.Property(p0.law, p0.defn, e, p0.loc)
    }

  }

  object Params {

    /**
      * Performs name resolution on the given constraint parameter `cparam0` in the given namespace `ns0`.
      */
    def resolve(cparam0: NamedAst.ConstraintParam, ns0: Name.NName, prog0: NamedAst.Program): Validation[ResolvedAst.ConstraintParam, ResolutionError] = cparam0 match {
      case NamedAst.ConstraintParam.HeadParam(sym, tpe, loc) => ResolvedAst.ConstraintParam.HeadParam(sym, tpe, loc).toSuccess
      case NamedAst.ConstraintParam.RuleParam(sym, tpe, loc) => ResolvedAst.ConstraintParam.RuleParam(sym, tpe, loc).toSuccess
    }

    /**
      * Performs name resolution on the given formal parameter `fparam0` in the given namespace `ns0`.
      */
    def resolve(fparam0: NamedAst.FormalParam, ns0: Name.NName, prog0: NamedAst.Program): Validation[ResolvedAst.FormalParam, ResolutionError] = {
      for {
        t <- lookupType(fparam0.tpe, ns0, prog0)
      } yield ResolvedAst.FormalParam(fparam0.sym, fparam0.mod, t, fparam0.loc)
    }

    /**
      * Performs name resolution on the given type parameter `tparam0` in the given namespace `ns0`.
      */
    def resolve(tparam0: NamedAst.TypeParam, ns0: Name.NName, prog0: NamedAst.Program): Validation[ResolvedAst.TypeParam, ResolutionError] = {
      ResolvedAst.TypeParam(tparam0.name, tparam0.tpe, tparam0.loc).toSuccess
    }

  }

  /**
    * The result of a definition lookup.
    */
  sealed trait DefTarget

  object DefTarget {

    case class Defn(defn: NamedAst.Def) extends DefTarget

    case class Hook(hook: Ast.Hook) extends DefTarget

  }

  /**
    * Finds the definition with the qualified name `qname` in the namespace `ns0`.
    */
  def lookupDef(qname: Name.QName, ns0: Name.NName, prog0: NamedAst.Program): Validation[DefTarget, ResolutionError] = {
    // check whether the name is fully-qualified.
    if (qname.isUnqualified) {
      // Case 1: Unqualified name. Lookup both the definition and the hook.
      val defnOpt = prog0.defs.getOrElse(ns0, Map.empty).get(qname.ident.name)
      val hookOpt = prog0.hooks.get(Symbol.mkDefnSym(ns0, qname.ident))

      (defnOpt, hookOpt) match {
        case (Some(defn), None) => DefTarget.Defn(defn).toSuccess
        case (None, Some(hook)) => DefTarget.Hook(hook).toSuccess
        case (None, None) =>
          // Try the global namespace.
          prog0.defs.getOrElse(Name.RootNS, Map.empty).get(qname.ident.name) match {
            case None => ResolutionError.UndefinedDef(qname, ns0, qname.loc).toFailure
            case Some(defn) => DefTarget.Defn(defn).toSuccess
          }
        case (Some(defn), Some(hook)) => ResolutionError.AmbiguousRef(qname, ns0, qname.loc).toFailure
      }
    } else {
      // Case 2: Qualified. Lookup both the definition and the hook.
      val defnOpt = prog0.defs.getOrElse(qname.namespace, Map.empty).get(qname.ident.name)
      val hookOpt = prog0.hooks.get(Symbol.mkDefnSym(qname.namespace, qname.ident))

      (defnOpt, hookOpt) match {
        case (Some(defn), None) => getDefIfAccessible(defn, ns0, qname.loc)
        case (None, Some(hook)) => DefTarget.Hook(hook).toSuccess
        case (None, None) => ResolutionError.UndefinedDef(qname, ns0, qname.loc).toFailure
        case (Some(defn), Some(hook)) => ResolutionError.AmbiguousRef(qname, ns0, qname.loc).toFailure
      }
    }
  }

  /**
    * Finds the enum definition matching the given qualified name and tag.
    */
  def lookupEnumByTag(qname: Option[Name.QName], tag: Name.Ident, ns: Name.NName, prog0: NamedAst.Program): Validation[NamedAst.Enum, ResolutionError] = {
    /*
     * Lookup the tag name in all enums across all namespaces.
     */
    val globalMatches = mutable.Set.empty[NamedAst.Enum]
    for ((_, decls) <- prog0.enums) {
      for ((enumName, decl) <- decls) {
        for ((tagName, caze) <- decl.cases) {
          if (tag.name == tagName) {
            globalMatches += decl
          }
        }
      }
    }

    // Case 1: Exact match found. Simply return it.
    if (globalMatches.size == 1) {
      return getEnumIfAccessible(globalMatches.head, ns, tag.loc)
    }

    // Case 2: No or multiple matches found.
    // Lookup the tag in either the fully qualified namespace or the current namespace.
    val namespace = if (qname.exists(_.isQualified)) qname.get.namespace else ns

    /*
     * Lookup the tag name in all enums in the current namespace.
     */
    val namespaceMatches = mutable.Set.empty[NamedAst.Enum]
    for ((enumName, decl) <- prog0.enums.getOrElse(namespace, Map.empty[String, NamedAst.Enum])) {
      for ((tagName, caze) <- decl.cases) {
        if (tag.name == tagName) {
          namespaceMatches += decl
        }
      }
    }

    // Case 2.1: Exact match found in namespace. Simply return it.
    if (namespaceMatches.size == 1) {
      return getEnumIfAccessible(namespaceMatches.head, ns, tag.loc)
    }

    // Case 2.2: No matches found in namespace.
    if (namespaceMatches.isEmpty) {
      return ResolutionError.UndefinedTag(tag.name, ns, tag.loc).toFailure
    }

    // Case 2.3: Multiple matches found in namespace and no enum name.
    if (qname.isEmpty) {
      val locs = namespaceMatches.map(_.sym.loc).toList.sorted
      return ResolutionError.AmbiguousTag(tag.name, ns, locs, tag.loc).toFailure
    }

    // Case 2.4: Multiple matches found in namespace and an enum name is available.
    val filteredMatches = namespaceMatches.filter(_.sym.name == qname.get.ident.name)
    if (filteredMatches.size == 1) {
      return getEnumIfAccessible(filteredMatches.head, ns, tag.loc)
    }

    ResolutionError.UndefinedTag(tag.name, ns, tag.loc).toFailure
  }

  /**
    * Finds the table of the given `qname` in the namespace `ns`.
    */
  def lookupTable(qname: Name.QName, ns: Name.NName, prog0: NamedAst.Program): Validation[NamedAst.Table, ResolutionError] = {
    if (qname.isUnqualified) {
      // Lookup in the current namespace.
      val tables = prog0.tables.getOrElse(ns, Map.empty)
      tables.get(qname.ident.name) match {
        case None => ResolutionError.UndefinedTable(qname, ns, qname.loc).toFailure
        case Some(table) => table.toSuccess
      }
    } else {
      // Lookup in the qualified namespace.
      val tables = prog0.tables.getOrElse(qname.namespace, Map.empty)
      tables.get(qname.ident.name) match {
        case None => ResolutionError.UndefinedTable(qname, qname.namespace, qname.loc).toFailure
        case Some(table) => table.toSuccess
      }
    }
  }

  /**
    * Returns `true` iff the given type `tpe0` is the Unit type.
    */
  def isUnitType(tpe: NamedAst.Type): Boolean = tpe match {
    case NamedAst.Type.Unit(loc) => true
    case _ => false
  }

  /**
    * Resolves the given type `tpe0` in the given namespace `ns0`.
    */
  // TODO: Add support for Higher-Kinded types.
  def lookupType(tpe0: NamedAst.Type, ns0: Name.NName, prog0: NamedAst.Program): Validation[Type, ResolutionError] = tpe0 match {
    case NamedAst.Type.Var(tvar, loc) => tvar.toSuccess
    case NamedAst.Type.Unit(loc) => Type.Unit.toSuccess
    case NamedAst.Type.Ambiguous(qname, loc) if qname.isUnqualified => qname.ident.name match {
      // Basic Types
      case "Unit" => Type.Unit.toSuccess
      case "Bool" => Type.Bool.toSuccess
      case "Char" => Type.Char.toSuccess
      case "Float" => Type.Float64.toSuccess
      case "Float32" => Type.Float32.toSuccess
      case "Float64" => Type.Float64.toSuccess
      case "Int" => Type.Int32.toSuccess
      case "Int8" => Type.Int8.toSuccess
      case "Int16" => Type.Int16.toSuccess
      case "Int32" => Type.Int32.toSuccess
      case "Int64" => Type.Int64.toSuccess
      case "BigInt" => Type.BigInt.toSuccess
      case "Str" => Type.Str.toSuccess
      case "Array" => Type.Array.toSuccess
      case "Native" => Type.Native.toSuccess
      case "Ref" => Type.Ref.toSuccess

      // Enum Types.
      case typeName =>
        // Lookup the enum in the current namespace.
        // If the namespace doesn't even exist, just use an empty map.
        val namespaceDecls = prog0.enums.getOrElse(ns0, Map.empty)
        namespaceDecls.get(typeName) match {
          case None =>
            // The enum was not found in the current namespace. Try the root namespace.
            val rootDecls = prog0.enums.getOrElse(Name.RootNS, Map.empty)
            rootDecls.get(typeName) match {
              case None => ResolutionError.UndefinedType(qname, ns0, loc).toFailure
              case Some(enum) => getTypeIfAccessible(enum, ns0, ns0.loc)
            }
          case Some(enum) => getTypeIfAccessible(enum, ns0, ns0.loc)
        }
    }
    case NamedAst.Type.Ambiguous(qname, loc) if qname.isQualified =>
      // Lookup the enum using the namespace.
      val decls = prog0.enums.getOrElse(qname.namespace, Map.empty)
      decls.get(qname.ident.name) match {
        case None => ResolutionError.UndefinedType(qname, ns0, loc).toFailure
        case Some(enum) => getTypeIfAccessible(enum, ns0, ns0.loc)
      }
    case NamedAst.Type.Enum(sym) =>
      Type.Enum(sym, Kind.Star).toSuccess
    case NamedAst.Type.Tuple(elms0, loc) =>
      for (
        elms <- seqM(elms0.map(tpe => lookupType(tpe, ns0, prog0)))
      ) yield Type.mkTuple(elms)
    case NamedAst.Type.Native(fqn, loc) =>
      // TODO: needs more precise type.
      Type.Native.toSuccess
    case NamedAst.Type.Arrow(tparams0, tresult0, loc) =>
      for (
        tparams <- seqM(tparams0.map(tpe => lookupType(tpe, ns0, prog0)));
        tresult <- lookupType(tresult0, ns0, prog0)
      ) yield Type.mkArrow(tparams, tresult)
    case NamedAst.Type.Apply(base0, targ0, loc) =>
      for (
        tpe1 <- lookupType(base0, ns0, prog0);
        tpe2 <- lookupType(targ0, ns0, prog0)
      ) yield Type.Apply(tpe1, tpe2)

  }

  /**
    * Successfully returns the given definition `defn0` if it is accessible from the given namespace `ns0`.
    *
    * Otherwise fails with a resolution error.
    *
    * A definition `defn0` is accessible from a namespace `ns0` if:
    *
    * (a) the definition is marked public, or
    * (b) the definition is defined in the namespace `ns0` itself or in a parent of `ns0`.
    */
  def getDefIfAccessible(defn0: NamedAst.Def, ns0: Name.NName, loc: SourceLocation): Validation[DefTarget, ResolutionError] = {
    //
    // Check if the definition is marked public.
    //
    if (defn0.mod.isPublic)
      return DefTarget.Defn(defn0).toSuccess

    //
    // Check if the definition is defined in `ns0` or in a parent of `ns0`.
    //
    val prefixNs = defn0.sym.namespace
    val targetNs = ns0.idents.map(_.name)
    if (targetNs.startsWith(prefixNs))
      return DefTarget.Defn(defn0).toSuccess

    //
    // The definition is not accessible.
    //
    ResolutionError.InaccessibleDef(defn0.sym, ns0, loc).toFailure
  }

  /**
    * Successfully returns the given `enum0` if it is accessible from the given namespace `ns0`.
    *
    * Otherwise fails with a resolution error.
    *
    * An enum is accessible from a namespace `ns0` if:
    *
    * (a) the definition is marked public, or
    * (b) the definition is defined in the namespace `ns0` itself or in a parent of `ns0`.
    */
  def getEnumIfAccessible(enum0: NamedAst.Enum, ns0: Name.NName, loc: SourceLocation): Validation[NamedAst.Enum, ResolutionError] = {
    //
    // Check if the definition is marked public.
    //
    if (enum0.mod.isPublic)
      return enum0.toSuccess

    //
    // Check if the enum is defined in `ns0` or in a parent of `ns0`.
    //
    val prefixNs = enum0.sym.namespace
    val targetNs = ns0.idents.map(_.name)
    if (targetNs.startsWith(prefixNs))
      return enum0.toSuccess

    //
    // The enum is not accessible.
    //
    ResolutionError.InaccessibleEnum(enum0.sym, ns0, loc).toFailure
  }

  /**
    * Successfully returns the type of the given `enum0` if it is accessible from the given namespace `ns0`.
    *
    * Otherwise fails with a resolution error.
    *
    * Internally uses [[getEnumIfAccessible]].
    */
  def getTypeIfAccessible(enum0: NamedAst.Enum, ns0: Name.NName, loc: SourceLocation): Validation[Type, ResolutionError] =
    getEnumIfAccessible(enum0, ns0, loc) map {
      case enum => Type.Enum(enum.sym, Kind.Star)
    }
}

