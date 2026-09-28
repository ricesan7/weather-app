#include "aie/Expression.hpp"

#include <cctype>
#include <stdexcept>
#include <vector>

namespace aie {
namespace {

enum class TokenType {
    Identifier,
    Number,
    And,
    Or,
    Eq,
    Ge,
    Le,
    Gt,
    Lt,
    LeftParen,
    RightParen,
    End,
};

struct Token {
    TokenType type;
    std::string text;
};

std::vector<Token> tokenize(const std::string& input) {
    std::vector<Token> tokens;
    std::size_t i = 0;

    while (i < input.size()) {
        if (std::isspace(static_cast<unsigned char>(input[i]))) {
            ++i;
            continue;
        }

        if (input.compare(i, 2, "&&") == 0) {
            tokens.push_back({TokenType::And, "&&"});
            i += 2;
        } else if (input.compare(i, 2, "||") == 0) {
            tokens.push_back({TokenType::Or, "||"});
            i += 2;
        } else if (input.compare(i, 2, "==") == 0) {
            tokens.push_back({TokenType::Eq, "=="});
            i += 2;
        } else if (input.compare(i, 2, ">=") == 0) {
            tokens.push_back({TokenType::Ge, ">="});
            i += 2;
        } else if (input.compare(i, 2, "<=") == 0) {
            tokens.push_back({TokenType::Le, "<="});
            i += 2;
        } else if (input[i] == '>') {
            tokens.push_back({TokenType::Gt, ">"});
            ++i;
        } else if (input[i] == '<') {
            tokens.push_back({TokenType::Lt, "<"});
            ++i;
        } else if (input[i] == '(') {
            tokens.push_back({TokenType::LeftParen, "("});
            ++i;
        } else if (input[i] == ')') {
            tokens.push_back({TokenType::RightParen, ")"});
            ++i;
        } else if (std::isdigit(static_cast<unsigned char>(input[i])) || input[i] == '.') {
            const auto start = i;
            while (i < input.size() &&
                   (std::isdigit(static_cast<unsigned char>(input[i])) ||
                    input[i] == '.' || input[i] == 's')) {
                ++i;
            }
            tokens.push_back({TokenType::Number, input.substr(start, i - start)});
        } else {
            const auto start = i;
            while (i < input.size() &&
                   !std::isspace(static_cast<unsigned char>(input[i])) &&
                   input[i] != '(' && input[i] != ')' &&
                   input[i] != '>' && input[i] != '<' &&
                   input[i] != '=' && input[i] != '&' && input[i] != '|') {
                ++i;
            }
            if (start == i) throw std::runtime_error("Invalid expression token");
            tokens.push_back({TokenType::Identifier, input.substr(start, i - start)});
        }
    }

    tokens.push_back({TokenType::End, ""});
    return tokens;
}

class Parser {
public:
    Parser(
        std::vector<Token> tokens,
        const std::unordered_map<std::string, Value>& context
    ) : tokens_(std::move(tokens)), context_(context) {}

    bool parse() {
        const bool result = parseOr();
        require(TokenType::End);
        return result;
    }

private:
    bool parseOr() {
        bool value = parseAnd();
        while (peek().type == TokenType::Or) {
            consume();
            const bool rhs = parseAnd();
            value = value || rhs;
        }
        return value;
    }

    bool parseAnd() {
        bool value = parseComparison();
        while (peek().type == TokenType::And) {
            consume();
            const bool rhs = parseComparison();
            value = value && rhs;
        }
        return value;
    }

    bool parseComparison() {
        if (peek().type == TokenType::LeftParen) {
            consume();
            const bool value = parseOr();
            require(TokenType::RightParen);
            return value;
        }

        const Value left = parseValue();
        const TokenType op = peek().type;
        if (op != TokenType::Eq && op != TokenType::Ge && op != TokenType::Le &&
            op != TokenType::Gt && op != TokenType::Lt) {
            return left.asBool();
        }

        consume();
        const Value right = parseValue();

        if (op == TokenType::Eq) {
            const auto ln = left.asNumber();
            const auto rn = right.asNumber();
            if (ln && rn) return *ln == *rn;
            return left.asString() == right.asString();
        }

        const auto ln = left.asNumber();
        const auto rn = right.asNumber();
        if (!ln || !rn) throw std::runtime_error("Ordered comparison requires numbers");

        if (op == TokenType::Ge) return *ln >= *rn;
        if (op == TokenType::Le) return *ln <= *rn;
        if (op == TokenType::Gt) return *ln > *rn;
        return *ln < *rn;
    }

    Value parseValue() {
        const auto token = consume();

        if (token.type == TokenType::Number) {
            std::string number = token.text;
            if (!number.empty() && number.back() == 's') number.pop_back();
            return Value(std::stod(number));
        }

        if (token.type != TokenType::Identifier) {
            throw std::runtime_error("Expected expression value");
        }

        if (token.text == "true") return Value(true);
        if (token.text == "false") return Value(false);

        const auto found = context_.find(token.text);
        if (found != context_.end()) return found->second;

        return Value(token.text);
    }

    const Token& peek() const {
        return tokens_.at(position_);
    }

    Token consume() {
        return tokens_.at(position_++);
    }

    void require(TokenType type) {
        if (peek().type != type) throw std::runtime_error("Unexpected expression token");
        consume();
    }

    std::vector<Token> tokens_;
    const std::unordered_map<std::string, Value>& context_;
    std::size_t position_ = 0;
};

} // namespace

bool ExpressionEvaluator::evaluate(
    const std::string& expression,
    const std::unordered_map<std::string, Value>& context
) const {
    return Parser(tokenize(expression), context).parse();
}

} // namespace aie
