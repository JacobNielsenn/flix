/*
 * Copyright 2017 Liam Palmer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

///
/// The Set type.
///
/// A set is currently represented internally as a list.
///
enum Set[t] {
    case Set(List[t])
}

namespace Set {

    ///
    /// Returns the size of `xs`.
    ///
    pub def size[a](xs: Set[a]): Int32 =
        let Set(s) = xs;
            List.length(s)






    ///
    /// Returns the empty set.
    /// `#{}` is syntactic sugar for `empty` (`#{} = empty()`).
    ///
    pub def empty[a](): Set[a] = Set(Nil)

    ///
    /// Returns the singleton set containing `x`.
    /// `#{x}` is syntactic sugar for `singleton` (`#{x} = singleton(x)`).
    ///
    pub def singleton[a](x: a): Set[a] = Set(x :: Nil)

    ///
    /// Adds `x` to `xs`.
    ///
    pub def insert[a](x: a, xs: Set[a]): Set[a] =
        let Set(s) = xs;
            if (List.memberOf(x, s)) xs else Set(x :: s)

    ///
    /// Removes `x` from `xs`.
    ///
    pub def delete[a](x: a, xs: Set[a]): Set[a] =
        let Set(s) = xs;
            Set(List.filter(e -> e != x, s))






    ///
    /// Returns true if and only if `xs` is the empty set, i.e. `Set(Nil)`.
    ///
    pub def isEmpty[a](xs: Set[a]): Bool =
        let Set(s) = xs;
            List.isEmpty(s)

    ///
    /// Returns true if and only if `x` is a member of `xs`.
    ///
    pub def memberOf[a](x: a, xs: Set[a]): Bool =
        let Set(s) = xs;
            List.memberOf(x, s)

    ///
    /// Returns true if and only if every element in `xs` appears in `ys`.
    ///
    pub def isSubsetOf[a](xs: Set[a], ys: Set[a]): Bool = forall(x -> memberOf(x, ys), xs)

    ///
    /// Returns true if and only if every element in `xs` appears in `ys` and `xs != ys`.
    ///
    pub def isProperSubsetOf[a](xs: Set[a], ys: Set[a]): Bool = if (isSubsetOf(xs, ys)) size(xs) != size(ys) else false

    ///
    /// Alias for `findLeft`.
    ///
    pub def find[a](f: a -> Bool, xs: Set[a]): Option[a] =
        let Set(s) = xs;
            List.find(f, s)

    ///
    /// Optionally returns the first element of `xs` that satisfies the predicate `f` when searching from left to right.
    ///
    pub def findLeft[a](f: a -> Bool, xs: Set[a]): Option[a] =
        let Set(s) = xs;
            List.findLeft(f, s)

    ///
    /// Optionally returns the first element of `xs` that satisfies the predicate `f` when searching from right to left.
    ///
    pub def findRight[a](f: a -> Bool, xs: Set[a]): Option[a] =
        let Set(s) = xs;
            List.findRight(f, s)






    ///
    /// Alias for `foldLeft`.
    ///
    pub def fold[a,b](f: (b, a) -> b, s: b, xs: Set[a]): b =
        let Set(es) = xs;
            List.fold(f, s, es)

    ///
    /// Applies `f` to a start value `s` and all elements in `xs` going from left to right.
    /// That is, the result is of the form: `f(...f(f(s, x1), x2)..., xn)`.
    ///
    pub def foldLeft[a,b](f: (b, a) -> b, s: b, xs: Set[a]): b =
        let Set(es) = xs;
            List.foldLeft(f, s, es)

    ///
    /// Applies `f` to a start value `s` and all elements in `xs` going from right to left.
    /// That is, the result is of the form: `f(x1, ...f(xn-1, f(xn, s))...)`.
    ///
    pub def foldRight[a,b](f: (a, b) -> b, s: b, xs: Set[a]): b =
        let Set(es) = xs;
            List.foldRight(f, s, es)

    ///
    /// Alias for `reduceLeftOpt`.
    ///
    pub def reduce[a](f: (a, a) -> a, xs: Set[a]): Option[a] =
        let Set(s) = xs;
            List.reduce(f, s)

    ///
    /// Applies `f` to all elements in `xs` going from left to right until a single value `v` is obtained.  Returns `Some(v)`.
    /// That is, the result is of the form: `Some(f(...f(f(x1, x2), x3)..., xn))`
    /// Returns `None` if `xs` is the empty set.
    ///
    pub def reduceLeft[a](f: (a, a) -> a, xs: Set[a]): Option[a] =
        let Set(s) = xs;
            List.reduceLeft(f, s)

    ///
    /// Applies `f` to all elements in `xs` going from right to left until a single value `v` is obtained.  Returns `Some(v)`.
    /// That is, the result is of the form: `Some(f(x1, ...f(xn-2, f(xn-1, xn))...))`
    /// Returns `None` if `xs` is the empty set.
    ///
    pub def reduceRight[a](f: (a, a) -> a, xs: Set[a]): Option[a] =
        let Set(s) = xs;
            List.reduceRight(f, s)






    ///
    /// Returns the number of elements in `xs` that satisfy the predicate `f`.
    ///
    pub def count[a](f: a -> Bool, xs: Set[a]): Int32 =
        let Set(s) = xs;
            List.count(f, s)

    ///
    /// Returns the union of the elements in `xs`.
    ///
    pub def flatten[a](xs: Set[Set[a]]): Set[a] = match xs with {
        case Set(Nil) => empty()
        case Set(x :: rs) => union(x, flatten(Set(rs)))
    }

    ///
    /// Returns `true` if and only if at least one element in `xs` satisfies the predicate `f`.
    /// Returns `false` if `xs` is the empty set.
    ///
    pub def exists[a](f: a -> Bool, xs: Set[a]): Bool =
        let Set(s) = xs;
            List.exists(f, s)

    ///
    /// Returns `true` if and only if all elements in `xs` satisfy the predicate `f`.
    /// Returns `true` if `xs` is the empty set.
    ///
    pub def forall[a](f: a -> Bool, xs: Set[a]): Bool =
        let Set(s) = xs;
            List.forall(f, s)






    ///
    /// Returns the union of `xs` and `ys`.
    ///
    pub def union[a](xs: Set[a], ys: Set[a]): Set[a] =
        let Set(s) = xs;
            List.foldRight(insert, ys, s)

    ///
    /// Returns the intersection of `xs` and `ys`.
    ///
    pub def intersection[a](xs: Set[a], ys: Set[a]): Set[a] =
        let Set(s) = xs;
            List.foldRight((x, y) -> if (memberOf(x, ys)) insert(x, y) else y, empty(), s)

    ///
    /// Returns the difference of `xs` and `ys`, i.e. `xs - ys`.
    ///
    pub def difference[a](xs: Set[a], ys: Set[a]): Set[a] =
        let Set(s) = xs;
            List.foldRight((x, y) -> if (memberOf(x, ys)) y else insert(x, y), empty(), s)

    ///
    /// Returns all subsets of `xs`.
    ///
    pub def subsets[a](xs: Set[a]): Set[Set[a]] =
        let Set(s) = xs;
            Set(List.map(x -> Set(x), List.subsequences(s)))






    ///
    /// Returns the set of all elements of `xs` that satisfy the predicate `f`.
    ///
    pub def filter[a](f: a -> Bool, xs: Set[a]): Set[a] =
        let Set(s) = xs;
            Set(List.filter(f, s))

    ///
    /// Returns the result of applying `f` to every element in `xs`.
    /// Note: The returned set may be smaller than `xs`.
    ///
    pub def map[a, b](f: a -> b, xs: Set[a]): Set[b] = match xs with {
        case Set(Nil) => Set(Nil)
        case Set(x :: rs) => insert(f(x), map(f, Set(rs)))
    }

    ///
    /// Returns the result of applying `f` to every element in `xs` and taking the union.
    ///
    pub def flatMap[a,b](f: a -> Set[b], xs: Set[a]): Set[b] = match xs with {
        case Set(Nil) => Set(Nil)
        case Set(x :: rs) => union(f(x), flatMap(f, Set(rs)))
    }

    ///
    /// Replaces the element `x` with `y` if `x` is in `xs`.
    /// Otherwise, returns `xs`.
    /// Note: The returned set may be smaller than `xs`.
    ///
    pub def replace[a](x: a, y: a, xs: Set[a]): Set[a] = map(e -> if (e == x) y else e, xs)

    ///
    /// Returns a pair of sets `(ys, zs)`.
    /// `ys` contains all elements of `xs` that satisfy the predicate `f`.
    /// `zs` contains all elements of `xs` that do not satisfy the predicate `f`.
    ///
    pub def partition[a](f: a -> Bool, xs: Set[a]): (Set[a], Set[a]) =
        let Set(s) = xs;
        let (ys, zs) = List.partition(f, s);
            (Set(ys), Set(zs))






    ///
    /// Returns the set `xs` as a list.
    ///
    pub def toList[a](xs: Set[a]): List[a] =
        let Set(s) = xs;
            s

    ///
    /// Returns the association set `xs` as a map.
    /// If `xs` contains multiple mappings with the same key, `toMap` does not
    /// make any guarantees about which mapping will be in the resulting map.
    ///
    pub def toMap[a, b](xs: Set[(a, b)]): Map[a, b] =
        let Set(s) = xs;
            List.toMap(s)






    ///
    /// Returns `true` if and only if `xs` and `ys` and equal, i.e. they have the same elements.
    ///
    pub def eq[a](xs: Set[a], ys: Set[a]): Bool = isSubsetOf(xs, ys) && size(xs) == size(ys)
}
