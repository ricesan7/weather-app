#pragma once

#include <cmath>
#include <cstdlib>
#include <optional>
#include <string>
#include <utility>
#include <variant>

namespace aie {

class Value {
public:
    using Storage = std::variant<double, bool, std::string>;

    Value() : value_(std::string()) {}
    Value(double value) : value_(value) {}
    Value(bool value) : value_(value) {}
    Value(std::string value) : value_(std::move(value)) {}
    Value(const char* value) : value_(std::string(value)) {}

    const Storage& storage() const { return value_; }

    bool asBool() const {
        if (const auto* b = std::get_if<bool>(&value_)) return *b;
        if (const auto* d = std::get_if<double>(&value_)) return std::fabs(*d) > 1e-12;
        const auto& s = std::get<std::string>(value_);
        return s == "true" || s == "TRUE" || s == "ON" || s == "1";
    }

    std::optional<double> asNumber() const {
        if (const auto* d = std::get_if<double>(&value_)) return *d;
        if (const auto* b = std::get_if<bool>(&value_)) return *b ? 1.0 : 0.0;
        const auto& s = std::get<std::string>(value_);
        char* end = nullptr;
        const double value = std::strtod(s.c_str(), &end);
        if (end != s.c_str() && *end == '\0') return value;
        return std::nullopt;
    }

    std::string asString() const {
        if (const auto* s = std::get_if<std::string>(&value_)) return *s;
        if (const auto* b = std::get_if<bool>(&value_)) return *b ? "true" : "false";
        const double d = std::get<double>(value_);
        std::string s = std::to_string(d);
        while (s.size() > 1 && s.back() == '0') s.pop_back();
        if (!s.empty() && s.back() == '.') s.pop_back();
        return s;
    }

private:
    Storage value_;
};

} // namespace aie
