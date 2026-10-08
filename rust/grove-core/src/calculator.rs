//! Bounded decimal arithmetic. Integers and decimal scales preserve exact JVM BigDecimal semantics.
use num_bigint::BigInt;
use num_integer::Integer;
use num_traits::{One, Signed, Zero};
use serde_json::{json, Value};

#[derive(Clone)]
struct Decimal {
    coefficient: BigInt,
    scale: i64,
}
fn ten(n: i64) -> BigInt {
    BigInt::from(10u8).pow(n as u32)
}
impl Decimal {
    fn normalize(mut self) -> Self {
        if self.coefficient.is_zero() {
            self.scale = 0;
            return self;
        }
        while (&self.coefficient % 10u8).is_zero() {
            self.coefficient /= 10u8;
            self.scale -= 1;
        }
        self
    }
    fn bound(self) -> Result<Self, &'static str> {
        if self.coefficient.abs().to_string().len() > 128 || self.scale.abs() > 128 {
            Err("LIMIT")
        } else {
            Ok(self)
        }
    }
    fn add(self, rhs: Self, subtract: bool) -> Result<Self, &'static str> {
        let scale = self.scale.max(rhs.scale);
        let left = self.coefficient * ten(scale - self.scale);
        let right = rhs.coefficient * ten(scale - rhs.scale);
        Self {
            coefficient: if subtract { left - right } else { left + right },
            scale,
        }
        .bound()
    }
    fn multiply(self, rhs: Self) -> Result<Self, &'static str> {
        Self {
            coefficient: self.coefficient * rhs.coefficient,
            scale: self.scale + rhs.scale,
        }
        .bound()
    }
    fn divide(self, rhs: Self, approximate: &mut bool) -> Result<Self, &'static str> {
        if rhs.coefficient.is_zero() {
            return Err("DIVISION_BY_ZERO");
        }
        let scale = self.scale - rhs.scale;
        let gcd = self.coefficient.gcd(&rhs.coefficient);
        let mut numerator = self.coefficient / gcd.clone();
        let mut denominator = rhs.coefficient / gcd;
        if denominator.is_negative() {
            numerator = -numerator;
            denominator = -denominator;
        }
        let mut residual = denominator.clone();
        let (mut twos, mut fives) = (0i64, 0i64);
        while (&residual % 2u8).is_zero() {
            residual /= 2u8;
            twos += 1;
        }
        while (&residual % 5u8).is_zero() {
            residual /= 5u8;
            fives += 1;
        }
        if residual.is_one() {
            let digits = twos.max(fives);
            return Self {
                coefficient: numerator
                    * BigInt::from(2u8).pow((digits - twos) as u32)
                    * BigInt::from(5u8).pow((digits - fives) as u32),
                scale: scale + digits,
            }
            .bound();
        }
        *approximate = true;
        let sign = if numerator.is_negative() { -1 } else { 1 };
        let numerator = numerator.abs();
        let mut exponent =
            numerator.to_string().len() as i64 - denominator.to_string().len() as i64;
        let less = if exponent >= 0 {
            numerator < denominator.clone() * ten(exponent)
        } else {
            numerator.clone() * ten(-exponent) < denominator
        };
        if less {
            exponent -= 1;
        }
        let shift = 33 - exponent;
        let (num, den) = if shift >= 0 {
            (numerator * ten(shift), denominator)
        } else {
            (numerator, denominator * ten(-shift))
        };
        let (mut q, r) = num.div_rem(&den);
        let twice = r * 2u8;
        if twice > den || (twice == den && q.is_odd()) {
            q += 1u8;
        }
        Self {
            coefficient: q * sign,
            scale: scale + shift,
        }
        .bound()
    }
    fn text(self) -> String {
        let value = self.normalize();
        let negative = value.coefficient.is_negative();
        let digits = value.coefficient.abs().to_string();
        let body = if value.scale <= 0 {
            digits + &"0".repeat((-value.scale) as usize)
        } else if value.scale as usize >= digits.len() {
            "0.".to_string() + &"0".repeat(value.scale as usize - digits.len()) + &digits
        } else {
            let split = digits.len() - value.scale as usize;
            digits[..split].to_string() + "." + &digits[split..]
        };
        if negative {
            "-".to_string() + &body
        } else {
            body
        }
    }
}
struct Parser {
    chars: Vec<char>,
    index: usize,
    operations: usize,
    approximate: bool,
}
impl Parser {
    fn peek(&self) -> char {
        *self.chars.get(self.index).unwrap_or(&'\0')
    }
    fn whitespace(&mut self) {
        while self.peek().is_whitespace() {
            self.index += 1;
        }
    }
    fn operator(&mut self) -> Result<(), &'static str> {
        self.index += 1;
        self.operations += 1;
        if self.operations > 128 {
            Err("LIMIT")
        } else {
            Ok(())
        }
    }
    fn expression(&mut self, depth: usize) -> Result<Decimal, &'static str> {
        let mut value = self.term(depth)?;
        loop {
            self.whitespace();
            let op = self.peek();
            if !matches!(op, '+' | '-' | '−') {
                return Ok(value);
            }
            self.operator()?;
            value = value.add(self.term(depth)?, op != '+')?;
        }
    }
    fn term(&mut self, depth: usize) -> Result<Decimal, &'static str> {
        let mut value = self.factor(depth)?;
        loop {
            self.whitespace();
            let op = self.peek();
            if !matches!(op, '*' | '×' | '/' | '÷') {
                return Ok(value);
            }
            self.operator()?;
            let right = self.factor(depth)?;
            value = if matches!(op, '*' | '×') {
                value.multiply(right)?
            } else {
                value.divide(right, &mut self.approximate)?
            };
        }
    }
    fn factor(&mut self, depth: usize) -> Result<Decimal, &'static str> {
        self.whitespace();
        let mut negative = false;
        while matches!(self.peek(), '+' | '-' | '−') {
            if self.peek() != '+' {
                negative = !negative;
            }
            self.operator()?;
            self.whitespace();
        }
        let mut value = if self.peek() == '(' {
            if depth >= 16 {
                return Err("LIMIT");
            }
            self.index += 1;
            let inner = self.expression(depth + 1)?;
            self.whitespace();
            if self.peek() != ')' {
                return Err("SYNTAX");
            }
            self.index += 1;
            inner
        } else {
            let start = self.index;
            let mut digits = String::new();
            let mut scale = 0i64;
            while self.peek().is_ascii_digit() {
                digits.push(self.peek());
                self.index += 1;
            }
            if self.peek() == '.' {
                self.index += 1;
                while self.peek().is_ascii_digit() {
                    digits.push(self.peek());
                    self.index += 1;
                    scale += 1;
                }
            }
            if digits.is_empty() {
                return Err("SYNTAX");
            }
            if self.index - start > 64 {
                return Err("LIMIT");
            }
            Decimal {
                coefficient: BigInt::parse_bytes(digits.as_bytes(), 10).ok_or("SYNTAX")?,
                scale,
            }
        };
        if negative {
            value.coefficient = -value.coefficient;
        }
        Ok(value)
    }
}
pub(crate) fn calculate(query: &str) -> Value {
    if query.encode_utf16().count() > 256 {
        return json!({"kind":"invalid","reason":"LIMIT"});
    }
    let text = query.trim();
    if !text.ends_with('=') || text.chars().any(|c| c.is_alphabetic()) {
        return json!({"kind":"none"});
    }
    let expression = text[..text.len() - 1].trim();
    let mut parser = Parser {
        chars: expression.chars().collect(),
        index: 0,
        operations: 0,
        approximate: false,
    };
    let result = (|| {
        let value = parser.expression(0)?;
        parser.whitespace();
        if parser.index != parser.chars.len() {
            return Err("SYNTAX");
        }
        let value = value.text();
        if value.len() > 128 {
            return Err("LIMIT");
        }
        Ok(value)
    })();
    match result {
        Ok(value) => {
            json!({"kind":"answer","expression":expression,"value":value,"approximate":parser.approximate})
        }
        Err(reason) => json!({"kind":"invalid","reason":reason}),
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn decimal_and_precedence() {
        for (q, want) in [
            ("0.1+0.2=", "0.3"),
            ("2+3*4=", "14"),
            ("(2+3)*4=", "20"),
            ("1/8=", "0.125"),
            ("1/3=", "0.3333333333333333333333333333333333"),
            ("-10/4=", "-2.5"),
            ("0/3=", "0"),
        ] {
            assert_eq!(calculate(q)["value"], want, "{q}");
        }
    }
    #[test]
    fn invalid_and_limits() {
        assert_eq!(calculate("1/0=")["reason"], "DIVISION_BY_ZERO");
        assert_eq!(calculate("1+=")["reason"], "SYNTAX");
        assert_eq!(calculate("settings")["kind"], "none");
        assert_eq!(calculate(&"1".repeat(257))["reason"], "LIMIT");
    }
}
